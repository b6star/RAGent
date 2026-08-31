import assert from "node:assert/strict";
import test from "node:test";

import {createSourceSnapshot, sha256} from "./manifest";
import {normalizeDocument, normalizeSnapshot} from "./document";

test(
  "document IDs are stable and source-specific metadata is preserved",
  () => {
    const snapshot = createSourceSnapshot(
      "github",
      "https://github.com/example/repo",
      "commit-1",
      [{
        key: "src/main.ts",
        url: "https://github.com/example/repo/blob/commit-1/src/main.ts",
        title: "src/main.ts",
        content: "export const value = 1;",
        contentHash: sha256("export const value = 1;"),
        byteSize: 23,
      }]
    );
    const first = normalizeDocument(snapshot, snapshot.items[0]);
    const second = normalizeDocument(snapshot, snapshot.items[0]);
    assert.equal(first.documentId, second.documentId);
    assert.equal(first.metadata.path, "src/main.ts");
    assert.equal(first.metadata.repository, "example/repo");
    assert.equal(first.sourceRevision, "commit-1");
  }
);

test("notion pages use page key as stable metadata identity", () => {
  const snapshot = createSourceSnapshot("notion", "https://app.notion.com/p/root", null, [
    {
      key: "notion:page-1",
      url: "https://app.notion.com/p/page-1",
      title: "Page 1",
      content: "content",
      contentHash: sha256("content"),
      byteSize: 7,
    },
  ]);
  const documents = normalizeSnapshot(snapshot);
  assert.equal(documents.length, 1);
  assert.equal(documents[0].metadata.pageId, "page-1");
  assert.equal(documents[0].metadata.sourceUrl, "https://app.notion.com/p/page-1");
});
