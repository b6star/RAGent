import fs from "node:fs";
import {mkdtemp, rm} from "node:fs/promises";
import os from "node:os";
import path from "node:path";

import {clone, listFiles, readBlob, resolveRef} from "isomorphic-git";
import http from "isomorphic-git/http/node";
import {Storage} from "firebase-admin/storage";

import {SOURCE_SYNC_CONFIG} from "./config";
import {SourceSyncWorkerError} from "./errors";
import {
  collectionResult,
  createSourceSnapshot,
  sha256,
  snapshotObjectPath,
  SourceCollectionResult,
  SourceSnapshotItem,
  uploadSourceSnapshot,
} from "./manifest";
import {canonicalizeGithubUrl} from "./urls";

const TEXT_DECODER = new TextDecoder("utf-8", {fatal: true});
type StorageBucket = ReturnType<Storage["bucket"]>;

/**
 * Collects a public GitHub repository using the public Git protocol.
 * @param {string} projectId Project document ID
 * @param {string} rawUrl Configured public repository URL
 * @param {StorageBucket} bucket Firebase Storage bucket
 * @return {Promise<SourceCollectionResult>} Persisted manifest metadata
 */
export async function collectGithubSource(
  projectId: string,
  rawUrl: string,
  bucket: StorageBucket
): Promise<SourceCollectionResult> {
  const canonicalUrl = canonicalizeGithubUrl(rawUrl);
  if (!canonicalUrl) {
    throw new SourceSyncWorkerError(
      "github_url_invalid",
      "GitHub 공개 저장소 URL 형식이 올바르지 않습니다.",
      false
    );
  }
  const startedAt = Date.now();
  const temporaryPrefix = path.join(os.tmpdir(), "ragent-github-");
  const repositoryDirectory = await mkdtemp(temporaryPrefix);

  try {
    await clone({
      fs,
      http,
      dir: repositoryDirectory,
      url: `${canonicalUrl}.git`,
      depth: 1,
      singleBranch: true,
      noCheckout: true,
      noTags: true,
      onProgress: () => assertRuntime(startedAt),
    });
    assertRuntime(startedAt);
    const commitOid = await resolveRef({
      fs,
      dir: repositoryDirectory,
      ref: "HEAD",
    });
    const filePaths = await listFiles({
      fs,
      dir: repositoryDirectory,
      ref: commitOid,
    });
    if (filePaths.length > SOURCE_SYNC_CONFIG.github.maximumFiles) {
      throw new SourceSyncWorkerError(
        "github_file_limit_exceeded",
        "GitHub 저장소의 파일 수가 동기화 한도를 초과했습니다.",
        false
      );
    }

    const items: SourceSnapshotItem[] = [];
    let totalBytes = 0;
    for (const filePath of [...filePaths].sort()) {
      assertRuntime(startedAt);
      const {blob} = await readBlob({
        fs,
        dir: repositoryDirectory,
        oid: commitOid,
        filepath: filePath,
      });
      if (blob.byteLength > SOURCE_SYNC_CONFIG.github.maximumFileBytes) {
        continue;
      }
      let content: string;
      try {
        content = TEXT_DECODER.decode(blob);
      } catch {
        continue;
      }
      totalBytes += blob.byteLength;
      if (totalBytes > SOURCE_SYNC_CONFIG.github.maximumTotalBytes) {
        throw new SourceSyncWorkerError(
          "github_size_limit_exceeded",
          "GitHub 저장소의 텍스트 용량이 동기화 한도를 초과했습니다.",
          false
        );
      }
      items.push({
        key: filePath,
        url: `${canonicalUrl}/blob/${commitOid}/${encodePath(filePath)}`,
        title: filePath,
        content,
        contentHash: sha256(blob),
        byteSize: blob.byteLength,
      });
    }
    const snapshot = createSourceSnapshot(
      "github",
      canonicalUrl,
      commitOid,
      items
    );
    const revisionId = `github-${snapshot.manifestHash}`;
    const objectPath = snapshotObjectPath(
      projectId,
      "github",
      revisionId
    );
    await uploadSourceSnapshot(bucket, objectPath, snapshot);
    return collectionResult(snapshot, objectPath);
  } catch (error) {
    if (error instanceof SourceSyncWorkerError) throw error;
    throw new SourceSyncWorkerError(
      "github_collection_failed",
      "공개 GitHub 저장소를 수집하지 못했습니다.",
      true
    );
  } finally {
    if (repositoryDirectory.startsWith(temporaryPrefix)) {
      await rm(repositoryDirectory, {recursive: true, force: true});
    }
  }
}

/**
 * Enforces the shared GitHub worker deadline.
 * @param {number} startedAt Worker start time in milliseconds
 */
function assertRuntime(startedAt: number): void {
  if (Date.now() - startedAt >
      SOURCE_SYNC_CONFIG.github.maximumRuntimeMilliseconds) {
    throw new SourceSyncWorkerError(
      "github_timeout",
      "GitHub 저장소 수집 시간이 초과되었습니다.",
      true
    );
  }
}

/**
 * URL-encodes every repository path segment while preserving separators.
 * @param {string} filePath Repository-relative path
 * @return {string} Encoded GitHub path
 */
function encodePath(filePath: string): string {
  return filePath.split("/").map(encodeURIComponent).join("/");
}
