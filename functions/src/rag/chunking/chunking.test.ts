/* eslint-disable require-jsdoc */

import assert from "node:assert/strict";
import test from "node:test";

import {sha256} from "../../sourceSync/manifest";
import {chunkDocument} from "./index";
import {NormalizedDocument} from "../../sourceSync/document";

function document(
  sourceType: "github" | "notion",
  content: string,
  path = "README.md"
): NormalizedDocument {
  return {
    documentId: sha256(`${sourceType}-${path}`),
    sourceType,
    sourceId: "source-1",
    canonicalUrl: "https://example.com/source",
    title: "Example document",
    content,
    contentHash: sha256(content),
    sourceRevision: "revision-1",
    extractorVersion: "extractor-1",
    metadata: {
      itemKey: path,
      sourceUrl: "https://example.com/source/item",
      repository: "owner/repo",
      path,
      pageId: "page-1",
    },
  };
}

test("Notion chunks preserve headings and remain deterministic", () => {
  const input = document(
    "notion",
    "# Overview\n\nShort introduction.\n\n## Setup\n\nInstall the app."
  );
  const first = chunkDocument(input);
  const second = chunkDocument(input);
  assert.deepEqual(first, second);
  assert.equal(first.length, 2);
  assert.match(first[0].content, /Example document > Overview/);
  assert.deepEqual(first[1].anchor.headingPath, ["Overview", "Setup"]);
  assert.equal(first[0].nextChunkId, first[1].chunkId);
});

test("GitHub Markdown chunks are split by heading sections", () => {
  const chunks = chunkDocument(document(
    "github",
    "# Intro\n\nInstall it.\n\n## API\n\nCall the endpoint."
  ));
  assert.equal(chunks.length, 2);
  assert.equal(chunks[0].anchor.symbol, "Intro");
  assert.equal(chunks[1].anchor.symbol, "API");
  assert.equal(chunks[1].anchor.lineStart, 5);
});

test("GitHub code chunks use symbols and fallback to one document", () => {
  const chunks = chunkDocument(document(
    "github",
    "class User {\n  name() { return \"x\"; }\n}\n" +
      "\nfunction load() { return true; }",
    "src/User.ts"
  ));
  assert.equal(chunks.length, 3);
  assert.equal(chunks[0].anchor.symbol, "User");
  assert.equal(chunks[2].anchor.symbol, "load");
  assert.equal(chunkDocument(document("github", "plain text", "data.bin"))
    .length, 1);
});
