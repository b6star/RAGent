/* eslint-disable require-jsdoc */

import {sha256} from "../../sourceSync/manifest";

export function normalizeChunkText(value: string): string {
  return value.replace(/\r\n/g, "\n").replace(/[ \t]+\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n").trim();
}

export function estimateTokens(value: string): number {
  return value.trim() ? value.trim().split(/\s+/).length : 0;
}

export function chunkContentHash(value: string): string {
  return sha256(normalizeChunkText(value));
}

export function stableChunkId(
  documentId: string,
  identity: string,
  segmentIndex: number
): string {
  return sha256([
    documentId,
    identity,
    segmentIndex.toString(),
  ].join("\u0000"));
}

export function splitByTokenWindow(
  value: string,
  maximumTokens: number,
  overlapTokens: number
): string[] {
  const words = normalizeChunkText(value).split(/\s+/).filter(Boolean);
  if (words.length <= maximumTokens) return [words.join(" ")];
  const result: string[] = [];
  const step = Math.max(1, maximumTokens - overlapTokens);
  for (let start = 0; start < words.length; start += step) {
    result.push(words.slice(start, start + maximumTokens).join(" "));
    if (start + maximumTokens >= words.length) break;
  }
  return result;
}

export function packBlocks(
  blocks: string[],
  targetTokens: number,
  maximumTokens: number,
  overlapTokens = 0
): string[] {
  const result: string[] = [];
  let current: string[] = [];
  let currentTokens = 0;
  for (const block of blocks) {
    const tokens = estimateTokens(block);
    if (current.length && currentTokens + tokens > targetTokens) {
      result.push(current.join("\n\n"));
      current = [];
      currentTokens = 0;
    }
    if (tokens > maximumTokens) {
      if (current.length) {
        result.push(current.join("\n\n"));
        current = [];
        currentTokens = 0;
      }
      result.push(...splitByTokenWindow(block, maximumTokens, overlapTokens));
      continue;
    }
    current.push(block);
    currentTokens += tokens;
  }
  if (current.length) result.push(current.join("\n\n"));
  return result;
}
