/* eslint-disable require-jsdoc */

import {Timestamp, getFirestore} from "firebase-admin/firestore";
import {sha256} from "../sourceSync/manifest";

import {RAG_CONFIG} from "./config";
import {ragReferences} from "./firestore";
import {
  RagError,
  RagRevisionStatus,
  canTransitionRagRevision,
  createEmbeddingJob,
  createRagMetadata,
  createRagRevision,
  RagRevisionDocument,
  validateEmbeddingContract,
} from "./model";

export function ragRevisionId(
  sourceRevisionIds: Record<string, string | null>
): string {
  const sourceKey = Object.entries(sourceRevisionIds)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([sourceType, revisionId]) => `${sourceType}:${revisionId ?? "none"}`)
    .join("|")
    .replace(/[^A-Za-z0-9:|_-]/g, "_");
  return `rag-${sha256(sourceKey).slice(0, 32)}`;
}

export async function createPendingRagRevision(
  projectId: string,
  sourceRevisionIds: Record<string, string | null>
): Promise<string> {
  const db = getFirestore();
  const references = ragReferences(db, projectId);
  const revisionId = ragRevisionId(sourceRevisionIds);
  const revision = references.revision(revisionId);
  const now = Timestamp.now();
  await db.runTransaction(async (transaction) => {
    const existing = await transaction.get(revision);
    if (existing.exists) {
      const existingData = existing.data() as RagRevisionDocument | undefined;
      if (existingData) validateEmbeddingContract(existingData.embedding);
      return;
    }
    const metadata = references.metadata;
    const metadataSnapshot = await transaction.get(metadata);
    if (!metadataSnapshot.exists) {
      transaction.create(metadata, createRagMetadata(now));
    }
    transaction.create(revision, createRagRevision(sourceRevisionIds, now));
    const job = references.embeddingJobs(revisionId).doc("default");
    transaction.create(job, createEmbeddingJob(revisionId, now));
  });
  return revisionId;
}

export async function promoteReadyRagRevision(
  projectId: string,
  revisionId: string
): Promise<void> {
  const db = getFirestore();
  const references = ragReferences(db, projectId);
  await db.runTransaction(async (transaction) => {
    const revision = await transaction.get(references.revision(revisionId));
    if (!revision.exists) throw new Error("RAG revision does not exist");
    const data = revision.data() as RagRevisionDocument;
    if (data.status !== "ready") {
      throw new Error("Only a ready RAG revision can become active");
    }
    validateEmbeddingContract(data.embedding);
    const now = Timestamp.now();
    transaction.set(references.metadata, {
      ...createRagMetadata(now),
      activeRagRevisionId: revisionId,
      activeEmbedding: data.embedding,
      updatedAt: now,
    }, {merge: true});
    transaction.update(references.revision(revisionId), {
      activeAt: now,
      updatedAt: now,
    });
  });
}

export function assertRagTransition(
  from: RagRevisionStatus,
  to: RagRevisionStatus
): void {
  if (!canTransitionRagRevision(from, to)) {
    throw new Error(`Invalid RAG revision transition: ${from} -> ${to}`);
  }
}

export function createRagError(
  code: string,
  message: string,
  retryable: boolean,
  occurredAt = Timestamp.now()
): RagError {
  return {
    code: code.slice(0, 80),
    message: message.slice(0, 500),
    retryable,
    occurredAt,
  };
}

export function validateRagLimits(
  documentCount: number,
  chunkCount: number
): void {
  if (documentCount < 0 ||
      documentCount > RAG_CONFIG.limits.maximumDocumentsPerRevision) {
    throw new Error("RAG revision document count exceeds the configured limit");
  }
  if (chunkCount < 0 ||
      chunkCount > RAG_CONFIG.limits.maximumChunksPerRevision) {
    throw new Error("RAG revision chunk count exceeds the configured limit");
  }
}
