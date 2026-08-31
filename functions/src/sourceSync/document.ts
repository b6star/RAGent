import {sha256, SourceSnapshot, SourceSnapshotItem} from "./manifest";
import {PublicSourceType} from "./model";

export type NormalizedDocument = {
  documentId: string;
  sourceType: PublicSourceType;
  sourceId: string;
  canonicalUrl: string;
  title: string | null;
  content: string;
  contentHash: string;
  sourceRevision: string | null;
  extractorVersion: string;
  metadata: {
    itemKey: string;
    sourceUrl: string | null;
    repository?: string;
    branch?: string;
    path?: string;
    blobId?: string | null;
    pageId?: string;
    parentPageId?: string | null;
    blockId?: string;
    headingPath?: string[];
    structureFingerprint?: string;
    symbol?: string;
    lineStart?: number;
    lineEnd?: number;
  };
};

/**
 * Converts one source snapshot item into the common Document shape.
 * @param {SourceSnapshot} snapshot Source snapshot
 * @param {SourceSnapshotItem} item Snapshot item
 * @return {NormalizedDocument} Normalized document
 */
export function normalizeDocument(
  snapshot: SourceSnapshot,
  item: SourceSnapshotItem
): NormalizedDocument {
  const sourceId = sha256(
    `${snapshot.sourceType}\u0000${snapshot.canonicalUrl}`
  );
  const documentId = sha256(`${sourceId}\u0000${item.key}`);
  const metadata = snapshot.sourceType === "github" ?
    githubMetadata(snapshot, item) : notionMetadata(item);
  return {
    documentId,
    sourceType: snapshot.sourceType,
    sourceId,
    canonicalUrl: snapshot.canonicalUrl,
    title: item.title,
    content: item.content,
    contentHash: item.contentHash,
    sourceRevision: snapshot.sourceRevision,
    extractorVersion: snapshot.extractorVersion,
    metadata,
  };
}

/**
 * Converts all snapshot items while preserving deterministic ordering.
 * @param {SourceSnapshot} snapshot Source snapshot
 * @return {NormalizedDocument[]} Normalized documents
 */
export function normalizeSnapshot(
  snapshot: SourceSnapshot
): NormalizedDocument[] {
  return snapshot.items
    .map((item) => normalizeDocument(snapshot, item))
    .sort((left, right) => left.documentId.localeCompare(right.documentId));
}

/**
 * Builds GitHub-specific metadata.
 * @param {SourceSnapshot} snapshot Source snapshot
 * @param {SourceSnapshotItem} item Snapshot item
 * @return {object} GitHub metadata
 */
function githubMetadata(snapshot: SourceSnapshot, item: SourceSnapshotItem) {
  const repository = new URL(snapshot.canonicalUrl).pathname
    .replace(/^\//, "");
  const path = item.key;
  const blobId = snapshot.sourceRevision;
  return {
    itemKey: item.key,
    sourceUrl: item.url,
    repository,
    path,
    blobId,
    symbol: item.anchor?.symbol,
    lineStart: item.anchor?.lineStart,
    lineEnd: item.anchor?.lineEnd,
  };
}

/**
 * Builds Notion-specific metadata.
 * @param {SourceSnapshotItem} item Snapshot item
 * @return {object} Notion metadata
 */
function notionMetadata(item: SourceSnapshotItem) {
  const pageId = item.key.replace(/^notion:/, "");
  return {
    itemKey: item.key,
    sourceUrl: item.url,
    pageId,
    parentPageId: item.anchor?.parentPageId ?? null,
    blockId: item.anchor?.blockId,
    headingPath: item.anchor?.headingPath,
    structureFingerprint: item.anchor?.structureFingerprint,
  };
}
