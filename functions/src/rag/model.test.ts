import assert from "node:assert/strict";
import test from "node:test";

import {
  canTransitionRagRevision,
  createEmbeddingContract,
  createEmbeddingJob,
  createRagRevision,
  createRagMetadata,
  isRagRevisionStatus,
  validateEmbeddingContract,
} from "./model";

const serverTime = {} as never;

test("RAG revision status validates the contract state machine", () => {
  assert.equal(isRagRevisionStatus("embedding"), true);
  assert.equal(isRagRevisionStatus("running"), false);
  assert.equal(canTransitionRagRevision("pending", "chunking"), true);
  assert.equal(canTransitionRagRevision("chunking", "embedding"), true);
  assert.equal(canTransitionRagRevision("embedding", "ready"), true);
  assert.equal(canTransitionRagRevision("ready", "pending"), false);
});

test("new revisions and jobs are pinned to one embedding contract", () => {
  const revision = createRagRevision(
    {github: "github-r1", notion: null},
    serverTime
  );
  const job = createEmbeddingJob("revision-1", serverTime);
  assert.equal(revision.status, "pending");
  assert.deepEqual(revision.embedding, createEmbeddingContract());
  assert.equal(revision.sourceRevisionIds.github, "github-r1");
  assert.equal(job.status, "queued");
  assert.equal(job.cursor, null);
  assert.equal(job.attempt, 0);
  assert.equal(createRagMetadata(serverTime).activeRagRevisionId, null);
});

test("a mismatched embedding contract is rejected", () => {
  const contract = createEmbeddingContract();
  assert.doesNotThrow(() => validateEmbeddingContract(contract));
  assert.throws(() => validateEmbeddingContract({
    ...contract,
    dimension: contract.dimension + 1,
  }));
});
