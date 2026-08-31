import {NormalizedDocument} from "../../sourceSync/document";

export type RagChunkAnchor = {
  headingPath?: string[];
  blockId?: string;
  structureFingerprint?: string;
  symbol?: string;
  lineStart?: number;
  lineEnd?: number;
};

export type RagChunk = {
  chunkId: string;
  documentId: string;
  sourceType: NormalizedDocument["sourceType"];
  sourceId: string;
  sourceRevision: string | null;
  canonicalUrl: string;
  title: string | null;
  content: string;
  contentHash: string;
  chunkerVersion: string;
  segmentIndex: number;
  anchor: RagChunkAnchor;
  previousChunkId: string | null;
  nextChunkId: string | null;
};

export type ChunkingOptions = {
  targetTokens?: number;
  maximumTokens?: number;
  overlapTokens?: number;
};
