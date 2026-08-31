/* eslint-disable require-jsdoc, valid-jsdoc, max-len */

import {getFunctions} from "firebase-admin/functions";
import {getFirestore, Timestamp} from "firebase-admin/firestore";

import {chunkDocument} from "./chunking";
import {RagChunk} from "./chunking/types";
import {RAG_CONFIG} from "./config";
import {ragReferences} from "./firestore";
import {assertRagTransition, createPendingRagRevision} from "./revision";
import {NormalizedDocument} from "../sourceSync/document";
import {sourceSyncReferences} from "../sourceSync/firestore";

const EMBEDDING_TASK_NAME = "embedRagRevisionTask";

/** Builds and queues a RAG revision from active normalized source documents. */
export async function stageRagRevision(
  projectId: string,
  sourceRevisionIds: Record<string, string | null>
): Promise<string | null> {
  const revisionId = await createPendingRagRevision(
    projectId,
    sourceRevisionIds
  );
  const db = getFirestore();
  const references = ragReferences(db, projectId);
  const revisionReference = references.revision(revisionId);
  const revisionSnapshot = await revisionReference.get();
  if (!revisionSnapshot.exists) throw new Error("RAG revision was not created");
  const revision = revisionSnapshot.data() as {
    status: "pending" | "chunking" | "embedding" | "ready" | "failed";
  };
  if (revision.status === "ready" || revision.status === "embedding") {
    return null;
  }
  assertRagTransition(revision.status, "chunking");

  const sourceReferences = sourceSyncReferences(db, projectId);
  const documents: NormalizedDocument[] = [];
  for (const sourceReference of [sourceReferences.github, sourceReferences.notion]) {
    const snapshot = await sourceReference.collection("documents")
      .where("status", "==", "active")
      .where("revisionState", "==", "active")
      .get();
    documents.push(...snapshot.docs.map((document) =>
      document.data() as NormalizedDocument
    ));
  }
  const chunks = documents.flatMap((document) => chunkDocument(document));
  if (!chunks.length) {
    await revisionReference.set({
      status: "failed",
      lastError: {
        code: "no_chunks",
        message: "No active chunks were produced for the RAG revision.",
        retryable: false,
        occurredAt: Timestamp.now(),
      },
      updatedAt: Timestamp.now(),
    }, {merge: true});
    return null;
  }

  await writeStagedChunks(projectId, revisionId, chunks);
  await reuseUnchangedVectors(projectId, revisionId, chunks);
  const now = Timestamp.now();
  await revisionReference.set({
    status: "chunking",
    documentCount: documents.length,
    chunkCount: chunks.length,
    startedAt: now,
    updatedAt: now,
  }, {merge: true});
  await getFunctions().taskQueue(EMBEDDING_TASK_NAME).enqueue({
    projectId,
    revisionId,
  });
  return revisionId;
}

async function reuseUnchangedVectors(
  projectId: string,
  revisionId: string,
  chunks: readonly RagChunk[]
): Promise<void> {
  const db = getFirestore();
  const references = ragReferences(db, projectId);
  const metadata = await references.metadata.get();
  const previousRevisionId = metadata.get("activeRagRevisionId");
  if (typeof previousRevisionId !== "string" ||
      previousRevisionId === revisionId) return;
  const previousVectors = await references.vectors(previousRevisionId).get();
  const reusable = new Map(
    previousVectors.docs
      .map((snapshot) => [snapshot.id, snapshot.data()] as const)
      .filter(([, data]) =>
        data.dimension === RAG_CONFIG.embedding.dimension &&
        data.model === RAG_CONFIG.embedding.model
      )
  );
  const candidates = chunks.filter((chunk) => {
    const previous = reusable.get(chunk.chunkId);
    return previous?.contentHash === chunk.contentHash &&
      previous.embedding !== undefined;
  });
  for (let index = 0; index < candidates.length; index += 200) {
    const batch = db.batch();
    candidates.slice(index, index + 200).forEach((chunk) => {
      const previous = reusable.get(chunk.chunkId)!;
      batch.set(references.vectors(revisionId).doc(chunk.chunkId), {
        ...previous,
        chunkId: chunk.chunkId,
        contentHash: chunk.contentHash,
        reusedFromRevisionId: previousRevisionId,
        updatedAt: Timestamp.now(),
      }, {merge: true});
      batch.set(references.chunks(revisionId).doc(chunk.chunkId), {
        embeddingStatus: "reused",
        updatedAt: Timestamp.now(),
      }, {merge: true});
    });
    await batch.commit();
  }
}

async function writeStagedChunks(
  projectId: string,
  revisionId: string,
  chunks: readonly RagChunk[]
): Promise<void> {
  const db = getFirestore();
  const references = ragReferences(db, projectId);
  for (let index = 0; index < chunks.length; index += 400) {
    const batch = db.batch();
    chunks.slice(index, index + 400).forEach((chunk) => {
      batch.set(references.chunks(revisionId).doc(chunk.chunkId), {
        ...chunk,
        revisionId,
        status: "pending",
        updatedAt: Timestamp.now(),
      }, {merge: true});
    });
    await batch.commit();
  }
}
