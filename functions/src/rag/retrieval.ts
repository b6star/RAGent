/* eslint-disable require-jsdoc, valid-jsdoc, max-len */

import {GoogleGenAI} from "@google/genai";
import {getFirestore} from "firebase-admin/firestore";
import {HttpsError, onCall} from "firebase-functions/v2/https";

import {embeddingApiKey} from "./embedding";
import {ragReferences} from "./firestore";
import {RAG_CONFIG} from "./config";
import {RagChunk} from "./chunking/types";
import {RagRevisionDocument, validateEmbeddingContract} from "./model";
import {SOURCE_SYNC_CONFIG} from "../sourceSync/config";
import {writeServerUsage} from "../usage";

const PROJECT_ID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;
const DEFAULT_TOP_K = 8;
const MAX_TOP_K = 20;
const MAX_QUERY_LENGTH = 20_000;

export type RagSearchResult = {
  chunkId: string;
  content: string;
  title: string | null;
  sourceType: RagChunk["sourceType"];
  sourceId: string;
  canonicalUrl: string;
  documentId: string;
  anchor: RagChunk["anchor"];
  distance: number;
};

/** Returns the nearest active-revision chunks for an authenticated project member. */
export const searchRagChunks = onCall({
  region: SOURCE_SYNC_CONFIG.region,
  secrets: [embeddingApiKey],
  enforceAppCheck: true,
  invoker: "public",
}, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }

  const projectId = typeof request.data?.projectId === "string" ?
    request.data.projectId.trim() : "";
  const query = typeof request.data?.query === "string" ?
    request.data.query.trim() : "";
  const requestedTopK = request.data?.topK;
  const topK = requestedTopK === undefined ? DEFAULT_TOP_K : requestedTopK;

  if (!PROJECT_ID_PATTERN.test(projectId)) {
    throw new HttpsError("invalid-argument", "A valid projectId is required.");
  }
  if (!query || query.length > MAX_QUERY_LENGTH) {
    throw new HttpsError("invalid-argument", "query must be 1-20000 characters.");
  }
  if (!Number.isInteger(topK) || topK < 1 || topK > MAX_TOP_K) {
    throw new HttpsError("invalid-argument", `topK must be 1-${MAX_TOP_K}.`);
  }

  const db = getFirestore();
  const references = ragReferences(db, projectId);
  const [projectSnapshot, memberSnapshot, metadataSnapshot] = await Promise.all([
    references.project.get(),
    references.project.collection("members").doc(request.auth.uid).get(),
    references.metadata.get(),
  ]);
  if (!projectSnapshot.exists) {
    throw new HttpsError("not-found", "Project was not found.");
  }
  if (projectSnapshot.get("ownerId") !== request.auth.uid &&
      !memberSnapshot.exists) {
    throw new HttpsError("permission-denied", "Project access is required.");
  }

  const activeRevisionId = metadataSnapshot.get("activeRagRevisionId");
  if (typeof activeRevisionId !== "string" || !activeRevisionId) {
    return {revisionId: null, results: [] as RagSearchResult[]};
  }
  const revisionSnapshot = await references.revision(activeRevisionId).get();
  if (!revisionSnapshot.exists) {
    throw new HttpsError("failed-precondition", "Active RAG revision is missing.");
  }
  const revision = revisionSnapshot.data() as RagRevisionDocument;
  if (revision.status !== "ready") {
    throw new HttpsError("failed-precondition", "Active RAG revision is not ready.");
  }
  validateEmbeddingContract(revision.embedding);

  const apiKey = embeddingApiKey.value();
  if (!apiKey) {
    throw new HttpsError("failed-precondition", "Embedding API key is not configured.");
  }
  const queryVector = await createQueryEmbedding(apiKey, query);
  await writeServerUsage({
    uid: request.auth.uid,
    usageId: `search-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
    category: "server_search",
    inputTokens: Math.ceil(query.length / 4),
    chunkCount: 1,
    characterCount: query.length,
    projectId,
    modelName: RAG_CONFIG.embedding.model,
  });
  const vectorSnapshot = await references.vectors(activeRevisionId)
    .findNearest({
      vectorField: "embedding",
      queryVector,
      limit: topK,
      distanceMeasure: "COSINE",
      distanceResultField: "distance",
    })
    .get();

  const results = await Promise.all(vectorSnapshot.docs.map(async (vector) => {
    const data = vector.data() as {chunkId?: unknown; distance?: unknown};
    const chunkId = typeof data.chunkId === "string" ? data.chunkId : vector.id;
    const chunkSnapshot = await references.chunks(activeRevisionId).doc(chunkId).get();
    if (!chunkSnapshot.exists) return null;
    const chunk = chunkSnapshot.data() as RagChunk;
    return {
      chunkId,
      content: chunk.content,
      title: chunk.title,
      sourceType: chunk.sourceType,
      sourceId: chunk.sourceId,
      canonicalUrl: chunk.canonicalUrl,
      documentId: chunk.documentId,
      anchor: chunk.anchor,
      distance: typeof data.distance === "number" ? data.distance : 0,
    } satisfies RagSearchResult;
  }));

  return {
    revisionId: activeRevisionId,
    results: results.filter((result): result is RagSearchResult => result !== null),
  };
});

async function createQueryEmbedding(apiKey: string, query: string): Promise<number[]> {
  const ai = new GoogleGenAI({apiKey});
  const response = await ai.models.embedContent({
    model: RAG_CONFIG.embedding.model,
    contents: [query],
    config: {
      taskType: "RETRIEVAL_QUERY",
      outputDimensionality: RAG_CONFIG.embedding.dimension,
    },
  });
  const values = response.embeddings?.[0]?.values ?? [];
  if (values.length !== RAG_CONFIG.embedding.dimension ||
      values.some((value) => !Number.isFinite(value))) {
    throw new HttpsError("internal", "Query embedding dimension is invalid.");
  }
  return values;
}
