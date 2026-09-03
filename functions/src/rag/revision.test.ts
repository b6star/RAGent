import assert from "node:assert/strict";
import test from "node:test";

import {
  assertRagTransition,
  createRagError,
  ragRevisionId,
  validateRagLimits,
} from "./revision";

// Source 순서가 달라도 동일한 RAG Revision ID가 생성되는지 확인한다.
test("RAG revision IDs are deterministic regardless of source order", () => {
  assert.equal(
    ragRevisionId({notion: "n1", github: "g1"}),
    ragRevisionId({github: "g1", notion: "n1"})
  );
  assert.match(ragRevisionId({github: "g1"}), /^rag-[a-f0-9]{32}$/);
});

// 상태 전이, 문서·Chunk 수 제한, 오류 메시지 길이 제한을 확인한다.
test("revision helpers enforce transitions, limits, and bounded errors", () => {
  assert.doesNotThrow(() => assertRagTransition("embedding", "ready"));
  assert.throws(() => assertRagTransition("ready", "embedding"));
  assert.doesNotThrow(() => validateRagLimits({
    documentCount: 1,
    chunkCount: 1,
  }));
  assert.throws(() => validateRagLimits({
    documentCount: 20_001,
    chunkCount: 1,
  }));
  const error = createRagError("x".repeat(100), "y".repeat(600), true);
  assert.equal(error.code.length, 80);
  assert.equal(error.message.length, 500);
});
