/* eslint-disable require-jsdoc, valid-jsdoc, max-len */

import {GoogleGenAI} from "@google/genai";
import {FieldValue, getFirestore, Timestamp} from "firebase-admin/firestore";
import {defineSecret} from "firebase-functions/params";
import {HttpsError, onCall} from "firebase-functions/v2/https";

import {RAG_CONFIG} from "./config";
import {ragReferences} from "./firestore";
import {RagChunk} from "./chunking/types";
import {RagRevisionDocument} from "./model";
import {assertRagTransition} from "./revision";

export const embeddingApiKey = defineSecret("GEMINI_EMBEDDING_API_KEY");

const PROJECT_ID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;

export type EmbeddingClient = {
  embedContents(texts: readonly string[]): Promise<readonly number[][]>;
};

export type EmbeddedChunk = {
  chunk: RagChunk;
  embedding: readonly number[];
};

/** Creates the Gemini Developer API client used only for document embeddings. */
export function createGeminiEmbeddingClient(apiKey: string): EmbeddingClient {
  const ai = new GoogleGenAI({apiKey});
  return {
    async embedContents(texts: readonly string[]): Promise<readonly number[][]> {
      if (!texts.length) return [];
      const response = await ai.models.embedContent({
        model: RAG_CONFIG.embedding.model,
        contents: [...texts],
        config: {
          taskType: "RETRIEVAL_DOCUMENT",
          outputDimensionality: RAG_CONFIG.embedding.dimension,
        },
      });
      const values = response.embeddings?.map((embedding) =>
        embedding.values ?? []
      ) ?? [];
      if (values.length !== texts.length) {
        throw new Error("Embedding response count does not match the batch");
      }
      values.forEach(validateEmbeddingDimension);
      return values;
    },
  };
}

/** Embeds chunks in bounded batches while preserving deterministic order. */
export async function embedChunks(
  chunks: readonly RagChunk[],
  client: EmbeddingClient
): Promise<EmbeddedChunk[]> {
  const result: EmbeddedChunk[] = [];
  for (let index = 0; index < chunks.length;
    index += RAG_CONFIG.embedding.maximumBatchSize) {
    const batch = chunks.slice(
      index, index + RAG_CONFIG.embedding.maximumBatchSize
    );
    const embeddings = await client.embedContents(
      batch.map((chunk) => chunk.content)
    );
    result.push(...batch.map((chunk, batchIndex) => ({
      chunk,
      embedding: embeddings[batchIndex],
    })));
  }
  return result;
}

/** Persists chunk metadata and Firestore-native vector values idempotently. */
export async function persistEmbeddedChunks(
  projectId: string,
  revisionId: string,
  embeddedChunks: readonly EmbeddedChunk[]
): Promise<void> {
  const db = getFirestore();
  const references = ragReferences(db, projectId);
  for (let index = 0; index < embeddedChunks.length; index += 400) {
    const batch = db.batch();
    embeddedChunks.slice(index, index + 400).forEach(({chunk, embedding}) => {
      const chunkReference = references.chunks(revisionId).doc(chunk.chunkId);
      const vectorReference = references.vectors(revisionId).doc(chunk.chunkId);
      batch.set(chunkReference, {
        ...chunk,
        revisionId,
        updatedAt: Timestamp.now(),
      }, {merge: true});
      batch.set(vectorReference, {
        chunkId: chunk.chunkId,
        documentId: chunk.documentId,
        contentHash: chunk.contentHash,
        embedding: FieldValue.vector([...embedding]),
        dimension: RAG_CONFIG.embedding.dimension,
        model: RAG_CONFIG.embedding.model,
        modelVersion: RAG_CONFIG.embedding.modelVersion,
        updatedAt: Timestamp.now(),
      }, {merge: true});
    });
    await batch.commit();
  }
}

/** Embeds the chunks already staged for one revision and marks it ready. */
export const runRagEmbedding = onCall({
  secrets: [embeddingApiKey],
  enforceAppCheck: true,
  invoker: "public",
}, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "로그인이 필요합니다.");
  }
  const projectId = typeof request.data?.projectId === "string" ?
    request.data.projectId.trim() : "";
  const revisionId = typeof request.data?.revisionId === "string" ?
    request.data.revisionId.trim() : "";
  if (!PROJECT_ID_PATTERN.test(projectId) || !revisionId) {
    throw new HttpsError("invalid-argument", "프로젝트와 revision을 확인해 주세요.");
  }

  const db = getFirestore();
  const references = ragReferences(db, projectId);
  const [projectSnapshot, memberSnapshot, revisionSnapshot] = await Promise.all([
    references.project.get(),
    references.project.collection("members").doc(request.auth.uid).get(),
    references.revision(revisionId).get(),
  ]);
  if (!projectSnapshot.exists) {
    throw new HttpsError("not-found", "프로젝트를 찾을 수 없습니다.");
  }
  if (projectSnapshot.get("ownerId") !== request.auth.uid &&
      !memberSnapshot.exists) {
    throw new HttpsError("permission-denied", "프로젝트 접근 권한이 없습니다.");
  }
  if (!revisionSnapshot.exists) {
    throw new HttpsError("not-found", "RAG revision을 찾을 수 없습니다.");
  }
  const revision = revisionSnapshot.data() as RagRevisionDocument;
  if (revision.status !== "chunking" && revision.status !== "embedding") {
    throw new HttpsError("failed-precondition", "임베딩을 시작할 수 없는 revision 상태입니다.");
  }

  const chunksSnapshot = await references.chunks(revisionId).get();
  const chunks = chunksSnapshot.docs.map((snapshot) =>
    snapshot.data() as RagChunk
  );
  if (!chunks.length) {
    throw new HttpsError("failed-precondition", "임베딩할 청크가 없습니다.");
  }
  const configuredApiKey = embeddingApiKey.value();
  if (!configuredApiKey) {
    throw new HttpsError(
      "failed-precondition",
      "Embedding API key is not configured."
    );
  }
  assertRagTransition(revision.status, "embedding");
  await references.revision(revisionId).set({
    status: "embedding",
    chunkCount: chunks.length,
    totalBatchCount: Math.ceil(
      chunks.length / RAG_CONFIG.embedding.maximumBatchSize
    ),
    startedAt: Timestamp.now(),
    updatedAt: Timestamp.now(),
  }, {merge: true});

  const apiKey = configuredApiKey;
  if (!apiKey) {
    throw new HttpsError("failed-precondition", "임베딩 API 키가 설정되지 않았습니다.");
  }
  const embedded = await embedChunks(
    chunks,
    createGeminiEmbeddingClient(apiKey)
  );
  await persistEmbeddedChunks(projectId, revisionId, embedded);
  const completedAt = Timestamp.now();
  await references.revision(revisionId).set({
    status: "ready",
    completedBatchCount: Math.ceil(
      chunks.length / RAG_CONFIG.embedding.maximumBatchSize
    ),
    completedAt,
    updatedAt: completedAt,
  }, {merge: true});
  await references.embeddingJobs(revisionId).doc("default").set({
    status: "completed",
    inputTokenCount: 0,
    outputTokenCount: 0,
    updatedAt: completedAt,
  }, {merge: true});
  return {
    revisionId,
    chunkCount: chunks.length,
    dimension: RAG_CONFIG.embedding.dimension,
    model: RAG_CONFIG.embedding.model,
    status: "ready",
  };
});

function validateEmbeddingDimension(values: readonly number[]): void {
  if (values.length !== RAG_CONFIG.embedding.dimension ||
      values.some((value) => !Number.isFinite(value))) {
    throw new Error(
      `Embedding dimension must be ${RAG_CONFIG.embedding.dimension}`
    );
  }
}
