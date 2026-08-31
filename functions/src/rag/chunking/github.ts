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

const MARKDOWN_EXTENSIONS = new Set([".md", ".markdown", ".mdx"]);

export function chunkGithubDocument(
  document: NormalizedDocument,
  options: ChunkingOptions = {}
): RagChunk[] {
  const target = options.targetTokens ?? RAG_CONFIG.chunking.targetTokens;
  const maximum = options.maximumTokens ?? RAG_CONFIG.chunking.maximumTokens;
  const isMarkdown = /\.(md|markdown|mdx)$/i.test(
    document.metadata.path ?? ""
  );
  const segments = isMarkdown ? parseMarkdown(document.content) :
    parseCodeSymbols(document.content);
  const chunks: RagChunk[] = [];
  for (const segment of segments) {
    const context = [
      document.metadata.repository,
      document.metadata.path,
      segment.symbol,
    ].filter(Boolean).join(" > ");
    const body = context ? `${context}\n\n${segment.content}` : segment.content;
    const pieces = packBlocks(
      [body],
      target,
      maximum,
      options.overlapTokens ?? RAG_CONFIG.chunking.overlapTokens
    );
    pieces.forEach((content, index) => {
      const anchor = {
        ...document.metadata,
        symbol: segment.symbol,
        lineStart: segment.lineStart,
        lineEnd: segment.lineEnd,
      };
      chunks.push({
        chunkId: stableChunkId(
          document.documentId,
          `${segment.identity}\u0000${index}`,
          0
        ),
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
        anchor,
        previousChunkId: null,
        nextChunkId: null,
      });
    });
  }
  return chunks.map((chunk, index) => ({
    ...chunk,
    segmentIndex: index,
    previousChunkId: chunks[index - 1]?.chunkId ?? null,
    nextChunkId: chunks[index + 1]?.chunkId ?? null,
  }));
}

type Segment = {
  identity: string;
  content: string;
  symbol?: string;
  lineStart?: number;
  lineEnd?: number;
};

function parseMarkdown(content: string): Segment[] {
  const lines = content.replace(/\r\n/g, "\n").split("\n");
  const segments: Segment[] = [];
  let title = "README";
  let start = 0;
  let buffer: string[] = [];
  const flush = (end: number) => {
    const value = normalizeChunkText(buffer.join("\n"));
    if (value) {
      segments.push({
        identity: `heading:${title}`,
        content: value,
        symbol: title,
        lineStart: start + 1,
        lineEnd: end,
      });
    }
    buffer = [];
  };
  lines.forEach((line, index) => {
    const heading = /^(#{1,6})\s+(.+?)\s*$/.exec(line);
    if (heading) {
      flush(index);
      title = heading[2];
      start = index;
    }
    buffer.push(line);
  });
  flush(lines.length);
  return segments.length ? segments : [{
    identity: "document",
    content,
    lineStart: 1,
    lineEnd: lines.length,
  }];
}

function parseCodeSymbols(content: string): Segment[] {
  const lines = content.replace(/\r\n/g, "\n").split("\n");
  const starts: Array<{line: number; symbol: string}> = [];
  lines.forEach((line, index) => {
    const match = /\b(?:class|interface|enum|function)\s+([A-Za-z_$][\w$]*)/
      .exec(line) ||
      /\b([A-Za-z_$][\w$]*)\s*\([^;{}]*\)\s*\{/.exec(line);
    if (match) {
      starts.push({line: index, symbol: match[1]});
    }
  });
  if (!starts.length) {
    return [{
      identity: "document",
      content,
      lineStart: 1,
      lineEnd: lines.length,
    }];
  }
  return starts.map((entry, index) => {
    const end = starts[index + 1]?.line ?? lines.length;
    return {
      identity: `symbol:${entry.symbol}`,
      content: lines.slice(entry.line, end).join("\n"),
      symbol: entry.symbol,
      lineStart: entry.line + 1,
      lineEnd: end,
    };
  });
}

export function isGithubMarkdownPath(path: string | undefined): boolean {
  const extension = path?.slice(path.lastIndexOf(".")).toLowerCase() ?? "";
  return MARKDOWN_EXTENSIONS.has(extension);
}
