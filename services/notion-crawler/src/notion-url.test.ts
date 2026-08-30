import assert from "node:assert/strict";
import test from "node:test";

import {mergeTextSnapshots} from "./crawler.js";
import {
  normalizeNotionUrl,
  notionPageKey,
  uniqueNotionLinks,
} from "./notion-url.js";
import {isPrivateAddress} from "./network-safety.js";

test("Notion URLs normalize tracking fields and share one page key", () => {
  const first = "https://example.notion.site/Page-" +
    "3c0974b809de8087b2b3d03c571039da?pvs=4#block";
  const second = "https://www.notion.so/Page-" +
    "3c0974b8-09de-8087-b2b3-d03c571039da?source=copy_link";
  assert.equal(
    normalizeNotionUrl(first),
    "https://example.notion.site/Page-3c0974b809de8087b2b3d03c571039da"
  );
  assert.equal(notionPageKey(first), notionPageKey(second));
});

test("Notion child links deduplicate by page ID", () => {
  const current = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  const links = uniqueNotionLinks([
    `https://example.notion.site/Page-${current}`,
    "https://example.notion.site/Child-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    "https://www.notion.so/Child-bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    "https://github.com/example/repository",
  ], current);
  assert.equal(links.length, 1);
});

test("scroll snapshots merge repeated text blocks", () => {
  assert.equal(
    mergeTextSnapshots(["first\n\nsecond", "second\n\nthird"]),
    "first\n\nsecond\n\nthird"
  );
});

test("private and metadata network ranges are rejected", () => {
  assert.equal(isPrivateAddress("127.0.0.1"), true);
  assert.equal(isPrivateAddress("169.254.169.254"), true);
  assert.equal(isPrivateAddress("10.0.0.1"), true);
  assert.equal(isPrivateAddress("8.8.8.8"), false);
});
