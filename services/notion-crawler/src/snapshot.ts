import {createHash} from "node:crypto";
import {gzip} from "node:zlib";
import {promisify} from "node:util";

import {Storage} from "@google-cloud/storage";

import {CrawlRequest} from "./contract.js";

const gzipAsync = promisify(gzip);

export type NotionSnapshotItem = {
  key: string;
  url: string;
  title: string;
  content: string;
  contentHash: string;
  byteSize: number;
};

export type NotionCollectionResult = {
  schemaVersion: number;
  manifestVersion: number;
  extractorVersion: string;
  sourceType: "notion";
  canonicalUrl: string;
  sourceRevision: null;
  manifestHash: string;
  itemCount: number;
  totalBytes: number;
  revisionId: string;
  snapshotObjectPath: string;
};

export function hashContent(value: string): string {
  return createHash("sha256").update(value).digest("hex");
}

export async function persistSnapshot(
  request: CrawlRequest,
  items: NotionSnapshotItem[]
): Promise<NotionCollectionResult> {
  const sortedItems = [...items].sort((left, right) =>
    left.key.localeCompare(right.key)
  );
  const manifestInput = sortedItems.map((item) => [
    item.key,
    item.contentHash,
    item.byteSize.toString(),
  ].join("\u0000")).join("\n");
  const manifestHash = hashContent(manifestInput);
  const revisionId = `notion-${manifestHash}`;
  const snapshotObjectPath = [
    request.storagePrefix,
    request.projectId,
    "notion",
    `${revisionId}.json.gz`,
  ].join("/");
  const totalBytes = sortedItems.reduce(
    (total, item) => total + item.byteSize,
    0
  );
  const snapshot = {
    schemaVersion: request.policy.schemaVersion,
    manifestVersion: request.policy.manifestVersion,
    extractorVersion: request.policy.extractorVersion,
    sourceType: "notion" as const,
    canonicalUrl: request.url,
    sourceRevision: null,
    manifestHash,
    itemCount: sortedItems.length,
    totalBytes,
    items: sortedItems,
  };
  const compressed = await gzipAsync(Buffer.from(JSON.stringify(snapshot)));
  if (process.env.SKIP_STORAGE === "true") {
    console.log("Notion snapshot storage skipped", {
      projectId: request.projectId,
      itemCount: sortedItems.length,
      totalBytes,
    });
    return {
      schemaVersion: request.policy.schemaVersion,
      manifestVersion: request.policy.manifestVersion,
      extractorVersion: request.policy.extractorVersion,
      sourceType: "notion",
      canonicalUrl: request.url,
      sourceRevision: null,
      manifestHash,
      itemCount: sortedItems.length,
      totalBytes,
      revisionId,
      snapshotObjectPath: "(storage skipped)",
    };
  }
  const bucket = new Storage().bucket(request.bucketName);
  await bucket.file(snapshotObjectPath).save(
    compressed,
    {
      resumable: false,
      metadata: {
        contentType: "application/json",
        contentEncoding: "gzip",
        cacheControl: "private, max-age=31536000, immutable",
      },
    }
  );
  const [files] = await bucket.getFiles({
    prefix: `${request.storagePrefix}/${request.projectId}/notion/`,
  });
  const revisions = await Promise.all(files.map(async (file) => {
    const [metadata] = await file.getMetadata();
    return {
      file,
      created: Date.parse(metadata.timeCreated ?? "") || 0,
    };
  }));
  revisions.sort((left, right) => right.created - left.created);
  await Promise.all(revisions.slice(30).map(({file}) => file.delete()));
  return {
    schemaVersion: request.policy.schemaVersion,
    manifestVersion: request.policy.manifestVersion,
    extractorVersion: request.policy.extractorVersion,
    sourceType: "notion",
    canonicalUrl: request.url,
    sourceRevision: null,
    manifestHash,
    itemCount: sortedItems.length,
    totalBytes,
    revisionId,
    snapshotObjectPath,
  };
}
