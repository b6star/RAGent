import assert from "node:assert/strict";
import test from "node:test";

import {
  canonicalizeGithubUrl,
  canonicalizeNotionUrl,
  notionPageKey,
} from "./urls";

test("public source URL validators reject unsupported hosts", () => {
  assert.equal(
    canonicalizeGithubUrl("https://github.com/OpenAI/example.git"),
    "https://github.com/OpenAI/example"
  );
  assert.equal(canonicalizeGithubUrl("https://example.com/a/b"), null);
  assert.equal(canonicalizeNotionUrl("http://example.notion.site/page"), null);
});

test("Notion tracking variants share one stable page ID", () => {
  const first = "https://example.notion.site/Page-" +
    "3c0974b809de8087b2b3d03c571039da?pvs=4";
  const second = "https://www.notion.so/Page-" +
    "3c0974b8-09de-8087-b2b3-d03c571039da?source=copy_link";
  assert.equal(notionPageKey(first), notionPageKey(second));
});
