/* eslint-disable require-jsdoc */

import assert from "node:assert/strict";
import test from "node:test";

import {embedChunks} from "./embedding";
import {RagChunk} from "./chunking/types";

function chunk(index: number): RagChunk {
  return {
    chunkId: `chunk-${index}`,
    documentId: "document-1",
    sourceType: "github",
    sourceId: "source-1",
    sourceRevision: "revision-1",
    canonicalUrl: "https://github.com/example/repo",
    title: "Example",
    content: `content-${index}`,
    contentHash: `hash-${index}`,
    chunkerVersion: "stable-chunker-v1",
    segmentIndex: index,
    anchor: {},
    previousChunkId: null,
    nextChunkId: null,
  };
}

test("embedding batches preserve chunk order", async () => {
  const requests: string[][] = [];
  const result = await embedChunks(
    [chunk(0), chunk(1), chunk(2)],
    {
      async embedContents(texts) {
        requests.push([...texts]);
        return texts.map((_, index) => [index + 1]);
      }
    }
  );

  assert.deepEqual(requests, [["content-0", "content-1", "content-2"]]);
  assert.deepEqual(result.map((item) => item.chunk.chunkId), [
    "chunk-0", "chunk-1", "chunk-2",
  ]);
  assert.deepEqual(result.map((item) => item.embedding), [[1], [2], [3]]);
});
