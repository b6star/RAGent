/* eslint-disable require-jsdoc */

import {NormalizedDocument} from "../../sourceSync/document";

import {chunkGithubDocument} from "./github";
import {chunkNotionDocument} from "./notion";
import {ChunkingOptions, RagChunk} from "./types";

export * from "./github";
export * from "./notion";
export * from "./types";

export function chunkDocument(
  document: NormalizedDocument,
  options?: ChunkingOptions
): RagChunk[] {
  return document.sourceType === "notion" ?
    chunkNotionDocument(document, options) :
    chunkGithubDocument(document, options);
}
