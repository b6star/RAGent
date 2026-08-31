/**
 * Single source of truth for the RAG revision and embedding contract.
 */
export const RAG_CONFIG = Object.freeze({
  schemaVersion: 1,
  contractVersion: "rag-contract-v1",
  chunkerVersion: "stable-chunker-v1",
  chunking: Object.freeze({
    targetTokens: 600,
    maximumTokens: 900,
    overlapTokens: 64,
  }),
  embedding: Object.freeze({
    provider: "gemini-developer" as const,
    model: "gemini-embedding-001",
    dimension: 768,
    modelVersion: "gemini-embedding-001-v1",
    maximumBatchSize: 32,
    maximumInputTokens: 2_048,
  }),
  job: Object.freeze({
    maximumAttempts: 3,
    leaseMilliseconds: 30 * 60 * 1000,
  }),
  limits: Object.freeze({
    maximumDocumentsPerRevision: 20_000,
    maximumChunksPerRevision: 100_000,
  }),
});

export type RagConfig = typeof RAG_CONFIG;
