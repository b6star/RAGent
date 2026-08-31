import assert from "node:assert/strict";
import test from "node:test";

import {compareSnapshots, createSourceSnapshot, sha256} from "./manifest";

test("manifest hashing is deterministic and sensitive to item content", () => {
  const itemA = {
    key: "a.ts",
    url: null,
    title: "a.ts",
    content: "alpha",
    contentHash: sha256("alpha"),
    byteSize: 5,
  };
  const itemB = {
    key: "b.ts",
    url: null,
    title: "b.ts",
    content: "beta",
    contentHash: sha256("beta"),
    byteSize: 4,
  };
  const first = createSourceSnapshot(
    "github",
    "https://github.com/example/repository",
    "commit",
    [itemB, itemA]
  );
  const second = createSourceSnapshot(
    "github",
    "https://github.com/example/repository",
    "commit",
    [itemA, itemB]
  );
  assert.equal(first.manifestHash, second.manifestHash);
  assert.deepEqual(first.items.map((item) => item.key), ["a.ts", "b.ts"]);

  const changed = createSourceSnapshot(
    "github",
    "https://github.com/example/repository",
    "commit-2",
    [{...itemA, contentHash: sha256("changed")}, itemB]
  );
  assert.notEqual(first.manifestHash, changed.manifestHash);
});

test(
  "snapshot comparison identifies added, modified, and deleted documents",
  () => {
    const previous = createSourceSnapshot(
      "notion", "https://example.com", null, [
        {key: "keep", url: null, title: "keep", content: "same",
          contentHash: sha256("same"), byteSize: 4},
        {key: "change", url: null, title: "change", content: "old",
          contentHash: sha256("old"), byteSize: 3},
        {key: "remove", url: null, title: "remove", content: "gone",
          contentHash: sha256("gone"), byteSize: 4},
      ]
    );
    const current = createSourceSnapshot(
      "notion", "https://example.com", null, [
        {key: "keep", url: null, title: "keep", content: "same",
          contentHash: sha256("same"), byteSize: 4},
        {key: "change", url: null, title: "change", content: "new",
          contentHash: sha256("new"), byteSize: 3},
        {key: "add", url: null, title: "add", content: "new",
          contentHash: sha256("new"), byteSize: 3},
      ]
    );
    assert.deepEqual(compareSnapshots(previous, current), {
      added: ["add"],
      modified: ["change"],
      deleted: ["remove"],
    });
  }
);
