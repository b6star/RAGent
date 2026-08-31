import assert from "node:assert/strict";
import test from "node:test";

import {
  assertRagTransition,
  createRagError,
  ragRevisionId,
  validateRagLimits,
} from "./revision";

test("RAG revision IDs are deterministic regardless of source order", () => {
  assert.equal(
    ragRevisionId({notion: "n1", github: "g1"}),
    ragRevisionId({github: "g1", notion: "n1"})
  );
  assert.match(ragRevisionId({github: "g1"}), /^rag-[a-f0-9]{32}$/);
});

test("revision helpers enforce transitions, limits, and bounded errors", () => {
  assert.doesNotThrow(() => assertRagTransition("embedding", "ready"));
  assert.throws(() => assertRagTransition("ready", "embedding"));
  assert.doesNotThrow(() => validateRagLimits(1, 1));
  assert.throws(() => validateRagLimits(20_001, 1));
  const error = createRagError("x".repeat(100), "y".repeat(600), true);
  assert.equal(error.code.length, 80);
  assert.equal(error.message.length, 500);
});
