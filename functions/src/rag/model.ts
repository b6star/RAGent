/* eslint-disable require-jsdoc */

import {FieldValue, Timestamp} from "firebase-admin/firestore";

import {RAG_CONFIG} from "./config";

export type RagFirestoreTime = Timestamp | FieldValue;

export const RAG_REVISION_STATUSES = [
  "pending",
  "chunking",
  "embedding",
  "ready",
  "failed",
] as const;

export type RagRevisionStatus = typeof RAG_REVISION_STATUSES[number];

export type EmbeddingContract = {
  provider: string;
  model: string;
  dimension: number;
  modelVersion: string;
};

export type RagRevisionDocument = {
  schemaVersion: number;
  contractVersion: string;
  status: RagRevisionStatus;
  sourceRevisionIds: Record<string, string | null>;
  embedding: EmbeddingContract;
  chunkerVersion: string;
  documentCount: number;
  chunkCount: number;
  completedBatchCount: number;
  totalBatchCount: number;
  activeAt: RagFirestoreTime | null;
  startedAt: RagFirestoreTime | null;
  completedAt: RagFirestoreTime | null;
  failedAt: RagFirestoreTime | null;
  lastError: RagError | null;
  createdAt: RagFirestoreTime;
  updatedAt: RagFirestoreTime;
};

export type RagMetadataDocument = {
  schemaVersion: number;
  contractVersion: string;
  activeRagRevisionId: string | null;
  activeEmbedding: EmbeddingContract | null;
  updatedAt: RagFirestoreTime;
};

export type RagError = {
  code: string;
  message: string;
  retryable: boolean;
  occurredAt: RagFirestoreTime;
};

export type EmbeddingJobDocument = {
  schemaVersion: number;
  revisionId: string;
  status: "queued" | "running" | "completed" | "failed";
  cursor: string | null;
  attempt: number;
  leaseOwner: string | null;
  leaseExpiresAt: RagFirestoreTime | null;
  inputTokenCount: number;
  outputTokenCount: number;
  estimatedCostMicros: number;
  lastError: RagError | null;
  createdAt: RagFirestoreTime;
  updatedAt: RagFirestoreTime;
};

const ALLOWED_TRANSITIONS: Readonly<
  Record<RagRevisionStatus, readonly RagRevisionStatus[]>
> = Object.freeze({
  pending: ["chunking", "failed"],
  chunking: ["embedding", "failed"],
  embedding: ["ready", "failed"],
  ready: [],
  failed: ["pending", "chunking", "embedding"],
});

export function isRagRevisionStatus(
  value: unknown
): value is RagRevisionStatus {
  return typeof value === "string" &&
    RAG_REVISION_STATUSES.some((status) => status === value);
}

export function canTransitionRagRevision(
  from: RagRevisionStatus,
  to: RagRevisionStatus
): boolean {
  return from === to || ALLOWED_TRANSITIONS[from].includes(to);
}

export function createEmbeddingContract(): EmbeddingContract {
  return {
    provider: RAG_CONFIG.embedding.provider,
    model: RAG_CONFIG.embedding.model,
    dimension: RAG_CONFIG.embedding.dimension,
    modelVersion: RAG_CONFIG.embedding.modelVersion,
  };
}

export function createRagRevision(
  sourceRevisionIds: Record<string, string | null>,
  serverTime: RagFirestoreTime
): RagRevisionDocument {
  return {
    schemaVersion: RAG_CONFIG.schemaVersion,
    contractVersion: RAG_CONFIG.contractVersion,
    status: "pending",
    sourceRevisionIds,
    embedding: createEmbeddingContract(),
    chunkerVersion: RAG_CONFIG.chunkerVersion,
    documentCount: 0,
    chunkCount: 0,
    completedBatchCount: 0,
    totalBatchCount: 0,
    activeAt: null,
    startedAt: null,
    completedAt: null,
    failedAt: null,
    lastError: null,
    createdAt: serverTime,
    updatedAt: serverTime,
  };
}

export function createRagMetadata(
  serverTime: RagFirestoreTime
): RagMetadataDocument {
  return {
    schemaVersion: RAG_CONFIG.schemaVersion,
    contractVersion: RAG_CONFIG.contractVersion,
    activeRagRevisionId: null,
    activeEmbedding: null,
    updatedAt: serverTime,
  };
}

export function createEmbeddingJob(
  revisionId: string,
  serverTime: RagFirestoreTime
): EmbeddingJobDocument {
  return {
    schemaVersion: RAG_CONFIG.schemaVersion,
    revisionId,
    status: "queued",
    cursor: null,
    attempt: 0,
    leaseOwner: null,
    leaseExpiresAt: null,
    inputTokenCount: 0,
    outputTokenCount: 0,
    estimatedCostMicros: 0,
    lastError: null,
    createdAt: serverTime,
    updatedAt: serverTime,
  };
}

export function validateEmbeddingContract(
  contract: EmbeddingContract
): void {
  if (contract.provider !== RAG_CONFIG.embedding.provider ||
      contract.model !== RAG_CONFIG.embedding.model ||
      contract.dimension !== RAG_CONFIG.embedding.dimension ||
      contract.modelVersion !== RAG_CONFIG.embedding.modelVersion) {
    throw new Error("Embedding contract does not match the configured index");
  }
}
