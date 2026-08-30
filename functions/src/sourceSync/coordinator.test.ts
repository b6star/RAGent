import assert from "node:assert/strict";
import test from "node:test";

import {decideSourceSyncRequest} from "./coordinator";

test("coordinator suppresses active leases and recent ready checks", () => {
  assert.deepEqual(
    decideSourceSyncRequest(1000, "job", 2000, null, "checking", false),
    {disposition: "in-progress", retryAfterMilliseconds: 0}
  );
  assert.deepEqual(
    decideSourceSyncRequest(1000, null, null, 2500, "ready", false),
    {disposition: "throttled", retryAfterMilliseconds: 1500}
  );
});

test("coordinator queues expired, failed, and changed-source requests", () => {
  assert.equal(
    decideSourceSyncRequest(2000, "old", 1000, 3000, "error", false)
      .disposition,
    "queued"
  );
  assert.equal(
    decideSourceSyncRequest(2000, "old", 4000, 3000, "ready", true)
      .disposition,
    "queued"
  );
});
