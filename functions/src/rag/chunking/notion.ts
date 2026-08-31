/* eslint-disable require-jsdoc */

import {RAG_CONFIG} from "../config";
import {NormalizedDocument} from "../../sourceSync/document";
import {RagChunk, ChunkingOptions} from "./types";
import {
  chunkContentHash,
  normalizeChunkText,
  packBlocks,
  stableChunkId,
} from "./text";

type NotionSection = {headingPath: string[]; blocks: string[]};

export function chunkNotionDocument(
  document: NormalizedDocument,
  options: ChunkingOptions = {}
): RagChunk[] {
  const sections = parseSections(document.content);
  const target = options.targetTokens ?? RAG_CONFIG.chunking.targetTokens;
  const maximum = options.maximumTokens ?? RAG_CONFIG.chunking.maximumTokens;
  const chunks: RagChunk[] = [];
  for (const section of sections) {
    const context = [document.title, ...section.headingPath]
      .filter((value): value is string => Boolean(value)).join(" > ");
    const blocks = section.blocks.map((block) =>
      context ? `${context}\n\n${block}` : block
    );
    const packed = packBlocks(
      blocks,
      target,
      maximum,
      options.overlapTokens ?? RAG_CONFIG.chunking.overlapTokens
    );
    packed.forEach((content, index) => {
      const anchor = {
        ...document.metadata,
        headingPath: section.headingPath,
      };
      const identity = [
        document.metadata.blockId ?? "section",
        section.headingPath.join("/"),
        index.toString(),
      ].join("\u0000");
      chunks.push(createChunk(document, content, anchor, identity));
    });
  }
  return linkAdjacentChunks(chunks);
}

function parseSections(content: string): NotionSection[] {
  const sections: NotionSection[] = [];
  let headingPath: string[] = [];
  let blocks: string[] = [];
  let currentLines: string[] = [];
  const flushBlock = () => {
    const block = normalizeChunkText(currentLines.join("\n"));
    if (block) blocks.push(block);
    currentLines = [];
  };
  const flushSection = () => {
    flushBlock();
    if (blocks.length) sections.push({headingPath, blocks});
    blocks = [];
  };
  for (const line of content.replace(/\r\n/g, "\n").split("\n")) {
    const heading = /^(#{1,6})\s+(.+?)\s*$/.exec(line);
    if (heading) {
      flushSection();
      const level = heading[1].length;
      headingPath = headingPath.slice(0, level - 1);
      headingPath.push(heading[2]);
      continue;
    }
    if (!line.trim() && currentLines.length) flushBlock();
    else if (line.trim() || currentLines.length) currentLines.push(line);
  }
  flushSection();
  return sections.length ? sections : [{headingPath: [], blocks: [content]}];
}

function createChunk(
  document: NormalizedDocument,
  content: string,
  metadata: RagChunk["anchor"],
  identity: string
): RagChunk {
  return {
    chunkId: stableChunkId(document.documentId, identity, 0),
    documentId: document.documentId,
    sourceType: document.sourceType,
    sourceId: document.sourceId,
    sourceRevision: document.sourceRevision,
    canonicalUrl: document.canonicalUrl,
    title: document.title,
    content,
    contentHash: chunkContentHash(content),
    chunkerVersion: RAG_CONFIG.chunkerVersion,
    segmentIndex: 0,
    anchor: metadata,
    previousChunkId: null,
    nextChunkId: null,
  };
}

function linkAdjacentChunks(chunks: RagChunk[]): RagChunk[] {
  return chunks.map((chunk, index) => ({
    ...chunk,
    segmentIndex: index,
    previousChunkId: chunks[index - 1]?.chunkId ?? null,
    nextChunkId: chunks[index + 1]?.chunkId ?? null,
  }));
}
