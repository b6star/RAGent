/* eslint-disable require-jsdoc, valid-jsdoc, max-len */

import {getFirestore, Timestamp} from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import {onTaskDispatched} from "firebase-functions/v2/tasks";

import {RAG_CONFIG} from "./config";
import {ragReferences} from "./firestore";
import {
  createGeminiEmbeddingClient,
  embedChunks,
  embeddingApiKey,
  persistEmbeddedChunks,
} from "./embedding";
import {RagChunk} from "./chunking/types";
import {promoteReadyRagRevision} from "./revision";

/** Runs the server-side embedding queue created after source synchronization. */
export const embedRagRevisionTask = onTaskDispatched({
  secrets: [embeddingApiKey],
  retryConfig: {maxAttempts: 3, maxBackoffSeconds: 300},
}, async (request) => {
  const projectId = typeof request.data?.projectId === "string" ?
    request.data.projectId.trim() : "";
  const revisionId = typeof request.data?.revisionId === "string" ?
    request.data.revisionId.trim() : "";
  if (!projectId || !revisionId) {
    throw new Error("Embedding task payload is invalid");
  }

  const references = ragReferences(getFirestore(), projectId);
  const revisionReference = references.revision(revisionId);
  const revisionSnapshot = await revisionReference.get();
  if (!revisionSnapshot.exists) throw new Error("RAG revision does not exist");
  const status = revisionSnapshot.get("status");
  if (status === "ready") return;
  if (status !== "chunking" && status !== "embedding") {
    throw new Error(`RAG revision cannot be embedded from status ${status}`);
  }

  const chunksSnapshot = await references.chunks(revisionId).get();
  const chunks = chunksSnapshot.docs
    .filter((snapshot) => snapshot.get("embeddingStatus") !== "reused" &&
      snapshot.get("embeddingStatus") !== "embedded")
    .map((snapshot) => snapshot.data() as RagChunk);
  if (!chunks.length) {
    const completedAt = Timestamp.now();
    await revisionReference.set({
      status: "ready",
      completedBatchCount: 0,
      completedAt,
      updatedAt: completedAt,
    }, {merge: true});
    await promoteReadyRagRevision(projectId, revisionId);
    await references.embeddingJobs(revisionId).doc("default").set({
      status: "completed",
      updatedAt: completedAt,
    }, {merge: true});
    return;
  }
  const apiKey = embeddingApiKey.value();
  if (!apiKey) throw new Error("Embedding API key is not configured");
  const now = Timestamp.now();
  await revisionReference.set({
    status: "embedding",
    chunkCount: chunks.length,
    totalBatchCount: Math.ceil(
      chunks.length / RAG_CONFIG.embedding.maximumBatchSize
    ),
    startedAt: now,
    updatedAt: now,
  }, {merge: true});
  await references.embeddingJobs(revisionId).doc("default").set({
    status: "running",
    attempt: request.retryCount + 1,
    updatedAt: now,
  }, {merge: true});

  try {
    const embedded = await embedChunks(
      chunks,
      createGeminiEmbeddingClient(apiKey)
    );
    await persistEmbeddedChunks(projectId, revisionId, embedded);
    const completedAt = Timestamp.now();
    await revisionReference.set({
      status: "ready",
      completedBatchCount: Math.ceil(
        chunks.length / RAG_CONFIG.embedding.maximumBatchSize
      ),
      completedAt,
      updatedAt: completedAt,
    }, {merge: true});
    await promoteReadyRagRevision(projectId, revisionId);
    await references.embeddingJobs(revisionId).doc("default").set({
      status: "completed",
      updatedAt: completedAt,
    }, {merge: true});
    logger.info("RAG embedding completed", {
      projectId,
      revisionId,
      chunkCount: chunks.length,
    });
  } catch (error) {
    logger.error("RAG embedding failed", {projectId, revisionId});
    throw error;
  }
});
