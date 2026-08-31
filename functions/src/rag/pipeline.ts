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
import {sha256} from "../sourceSync/manifest";
import {PublicSourceType} from "../sourceSync/model";
import {SOURCE_SYNC_CONFIG} from "../sourceSync/config";

const EMBEDDING_TASK_NAME = [
  "locations",
  SOURCE_SYNC_CONFIG.region,
  "functions",
  "embedRagRevisionTask",
].join("/");

/** Builds and queues a RAG revision from active normalized source documents. */
export async function stageRagRevision(
  projectId: string,
  sourceRevisionIds: Record<string, string | null>,
  triggeredBy?: string
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
  const documents = await loadActiveDocuments(sourceReferences);
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
    ...(triggeredBy ? {triggeredBy} : {}),
  });
  return revisionId;
}

async function loadActiveDocuments(
  sourceReferences: ReturnType<typeof sourceSyncReferences>
): Promise<NormalizedDocument[]> {
  const documents: NormalizedDocument[] = [];
  for (const sourceReference of [sourceReferences.github, sourceReferences.notion]) {
    const snapshot = await sourceReference.collection("documents").get();
    const activeDocuments = snapshot.docs
      .filter((document) => document.get("status") === "active" &&
        document.get("revisionState") === "active")
      .map((document) => document.data() as NormalizedDocument);
    if (activeDocuments.length) {
      documents.push(...activeDocuments);
      continue;
    }
    const source = await sourceReference.get();
    const flatDocument = source.exists ? flatSourceDocument(
      sourceReference.id as PublicSourceType,
      source.data() ?? {}
    ) : null;
    if (flatDocument) documents.push(flatDocument);
  }
  return documents;
}

function flatSourceDocument(
  sourceType: PublicSourceType,
  data: Record<string, unknown>
): NormalizedDocument | null {
  const content = typeof data.content === "string" ? data.content : "";
  const canonicalUrl = typeof data.canonicalUrl === "string" ?
    data.canonicalUrl : "";
  if (!content.trim() || !canonicalUrl) return null;
  const itemKey = typeof data.itemKey === "string" ? data.itemKey : sourceType;
  const sourceId = typeof data.sourceId === "string" ? data.sourceId :
    sha256(`${sourceType}\u0000${canonicalUrl}`);
  const documentId = typeof data.documentId === "string" ? data.documentId :
    sha256(`${sourceId}\u0000${itemKey}`);
  const contentHash = typeof data.contentHash === "string" ?
    data.contentHash : sha256(content);
  const sourceRevision = typeof data.sourceRevision === "string" ?
    data.sourceRevision : null;
  const title = typeof data.title === "string" ? data.title : null;
  const sourceUrl = typeof data.sourceUrl === "string" ? data.sourceUrl :
    canonicalUrl;
  return {
    documentId,
    sourceType,
    sourceId,
    canonicalUrl,
    title,
    content,
    contentHash,
    sourceRevision,
    extractorVersion: typeof data.extractorVersion === "string" ?
      data.extractorVersion : "flat-source-v1",
    metadata: sourceType === "github" ? {
      itemKey,
      sourceUrl,
      repository: typeof data.repository === "string" ? data.repository :
        new URL(canonicalUrl).pathname.replace(/^\//, ""),
      path: typeof data.path === "string" ? data.path : itemKey,
      blobId: typeof data.blobId === "string" ? data.blobId : sourceRevision,
      ...(typeof data.symbol === "string" ? {symbol: data.symbol} : {}),
      ...(typeof data.lineStart === "number" ?
        {lineStart: data.lineStart} : {}),
      ...(typeof data.lineEnd === "number" ? {lineEnd: data.lineEnd} : {}),
    } : {
      itemKey,
      sourceUrl,
      pageId: typeof data.pageId === "string" ? data.pageId : itemKey,
      parentPageId: typeof data.parentPageId === "string" ?
        data.parentPageId : null,
      ...(typeof data.blockId === "string" ? {blockId: data.blockId} : {}),
      ...(Array.isArray(data.headingPath) ? {
        headingPath: data.headingPath.filter((value): value is string =>
          typeof value === "string"),
      } : {}),
      ...(typeof data.structureFingerprint === "string" ? {
        structureFingerprint: data.structureFingerprint,
      } : {}),
    },
  };
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
