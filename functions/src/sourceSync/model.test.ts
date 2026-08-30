import assert from "node:assert/strict";
import test from "node:test";

import {
  canTransitionSourceSyncStatus,
  createInitialProjectSource,
  createInitialSourceSyncControl,
  createInitialSourceSyncStatus,
  isPublicSourceType,
  isSourceSyncStatus,
} from "./model";

const serverTime = {} as never;

test("status and source type validators reject unknown protocol values", () => {
  assert.equal(isSourceSyncStatus("checking"), true);
  assert.equal(isSourceSyncStatus("running"), false);
  assert.equal(isPublicSourceType("github"), true);
  assert.equal(isPublicSourceType("gitlab"), false);
});

test("status machine accepts normal sync and retry flows", () => {
  assert.equal(canTransitionSourceSyncStatus("idle", "queued"), true);
  assert.equal(canTransitionSourceSyncStatus("queued", "checking"), true);
  assert.equal(canTransitionSourceSyncStatus("checking", "changed"), true);
  assert.equal(canTransitionSourceSyncStatus("changed", "ready"), true);
  assert.equal(canTransitionSourceSyncStatus("error", "queued"), true);
  assert.equal(canTransitionSourceSyncStatus("ready", "checking"), false);
  assert.equal(canTransitionSourceSyncStatus("idle", "ready"), false);
});

test("initial documents use the shared schema and manifest versions", () => {
  const status = createInitialSourceSyncStatus(serverTime);
  const control = createInitialSourceSyncControl(serverTime);
  const source = createInitialProjectSource(
    "notion",
    "https://example.notion.site/public-page",
    serverTime
  );

  assert.equal(status.status, "idle");
  assert.equal(control.attempt, 0);
  assert.equal(source.sourceType, "notion");
  assert.equal(source.status, "idle");
  assert.equal(source.manifestHash, null);
  assert.equal(source.schemaVersion, status.schemaVersion);
});
