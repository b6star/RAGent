import {createHash} from "node:crypto";
import {gzip} from "node:zlib";
import {promisify} from "node:util";

import {Storage} from "firebase-admin/storage";

import {SOURCE_SYNC_CONFIG} from "./config";
import {PublicSourceType} from "./model";

const gzipAsync = promisify(gzip);
type StorageBucket = ReturnType<Storage["bucket"]>;

export type SourceSnapshotItem = {
  key: string;
  url: string | null;
  title: string | null;
  content: string;
  contentHash: string;
  byteSize: number;
};

export type SourceSnapshot = {
  schemaVersion: number;
  manifestVersion: number;
  extractorVersion: string;
  sourceType: PublicSourceType;
  canonicalUrl: string;
  sourceRevision: string | null;
  manifestHash: string;
  itemCount: number;
  totalBytes: number;
  items: SourceSnapshotItem[];
};

export type SourceCollectionResult = Omit<SourceSnapshot, "items"> & {
  revisionId: string;
  snapshotObjectPath: string;
};

export type ManifestChangeSet = {
  added: string[];
  modified: string[];
  deleted: string[];
};

/**
 * Compares two snapshots by stable document key and content hash.
 * @param {SourceSnapshot|null} previous Previously active snapshot
 * @param {SourceSnapshot} current Newly collected snapshot
 * @return {ManifestChangeSet} Added, modified, and deleted document keys
 */
export function compareSnapshots(
  previous: SourceSnapshot | null,
  current: SourceSnapshot
): ManifestChangeSet {
  const extractorChanged = previous !== null &&
    previous.extractorVersion !== current.extractorVersion;
  const before = new Map(
    (previous?.items ?? []).map((item) => [item.key, item.contentHash])
  );
  const after = new Map(
    current.items.map((item) => [item.key, item.contentHash])
  );
  const added: string[] = [];
  const modified: string[] = [];
  const deleted: string[] = [];
  for (const [key, hash] of after) {
    if (!before.has(key)) added.push(key);
    else if (extractorChanged || before.get(key) !== hash) modified.push(key);
  }
  for (const key of before.keys()) {
    if (!after.has(key)) deleted.push(key);
  }
  return {
    added: added.sort(),
    modified: modified.sort(),
    deleted: deleted.sort(),
  };
}

/**
 * Calculates a hexadecimal SHA-256 content hash.
 * @param {string|Uint8Array} value Content to hash
 * @return {string} Hexadecimal digest
 */
export function sha256(value: string | Uint8Array): string {
  return createHash(SOURCE_SYNC_CONFIG.hashAlgorithm).update(value).digest(
    "hex"
  );
}

/**
 * Creates a deterministic source snapshot and manifest hash.
 * @param {PublicSourceType} sourceType GitHub or Notion
 * @param {string} canonicalUrl Canonical source URL
 * @param {string|null} sourceRevision Upstream Git commit or page revision
 * @param {SourceSnapshotItem[]} items Extracted textual items
 * @return {SourceSnapshot} Sorted immutable snapshot payload
 */
export function createSourceSnapshot(
  sourceType: PublicSourceType,
  canonicalUrl: string,
  sourceRevision: string | null,
  items: SourceSnapshotItem[]
): SourceSnapshot {
  const sortedItems = [...items].sort((left, right) =>
    left.key.localeCompare(right.key)
  );
  const manifestInput = sortedItems.map((item) => [
    item.key,
    item.contentHash,
    item.byteSize.toString(),
  ].join("\u0000")).join("\n");
  const totalBytes = sortedItems.reduce(
    (total, item) => total + item.byteSize,
    0
  );
  return {
    schemaVersion: SOURCE_SYNC_CONFIG.schemaVersion,
    manifestVersion: SOURCE_SYNC_CONFIG.manifestVersion,
    extractorVersion: SOURCE_SYNC_CONFIG.extractorVersion,
    sourceType,
    canonicalUrl,
    sourceRevision,
    manifestHash: sha256(manifestInput),
    itemCount: sortedItems.length,
    totalBytes,
    items: sortedItems,
  };
}

/**
 * Returns the stable Cloud Storage path for an immutable revision snapshot.
 * @param {string} projectId Project document ID
 * @param {PublicSourceType} sourceType GitHub or Notion
 * @param {string} revisionId Content-derived revision ID
 * @return {string} Object path
 */
export function snapshotObjectPath(
  projectId: string,
  sourceType: PublicSourceType,
  revisionId: string
): string {
  return [
    SOURCE_SYNC_CONFIG.storagePrefix,
    projectId,
    sourceType,
    `${revisionId}.json.gz`,
  ].join("/");
}

/**
 * Uploads an immutable compressed source snapshot.
 * @param {StorageBucket} bucket Firebase Storage bucket
 * @param {string} objectPath Destination object path
 * @param {SourceSnapshot} snapshot Snapshot payload
 * @return {Promise<void>} Upload completion
 */
export async function uploadSourceSnapshot(
  bucket: StorageBucket,
  objectPath: string,
  snapshot: SourceSnapshot
): Promise<void> {
  const compressed = await gzipAsync(Buffer.from(JSON.stringify(snapshot)));
  await bucket.file(objectPath).save(compressed, {
    resumable: false,
    metadata: {
      contentType: "application/json",
      contentEncoding: "gzip",
      cacheControl: "private, max-age=31536000, immutable",
    },
  });
}

/**
 * Builds the metadata returned by a collector after snapshot upload.
 * @param {SourceSnapshot} snapshot Uploaded snapshot
 * @param {string} objectPath Cloud Storage object path
 * @return {SourceCollectionResult} Persistable collection result
 */
export function collectionResult(
  snapshot: SourceSnapshot,
  objectPath: string
): SourceCollectionResult {
  return {
    schemaVersion: snapshot.schemaVersion,
    manifestVersion: snapshot.manifestVersion,
    extractorVersion: snapshot.extractorVersion,
    sourceType: snapshot.sourceType,
    canonicalUrl: snapshot.canonicalUrl,
    sourceRevision: snapshot.sourceRevision,
    manifestHash: snapshot.manifestHash,
    itemCount: snapshot.itemCount,
    totalBytes: snapshot.totalBytes,
    revisionId: `${snapshot.sourceType}-${snapshot.manifestHash}`,
    snapshotObjectPath: objectPath,
  };
}
