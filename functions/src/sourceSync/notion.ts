import {GoogleAuth} from "google-auth-library";

import {SOURCE_SYNC_CONFIG} from "./config";
import {SourceSyncWorkerError} from "./errors";
import {SourceCollectionResult} from "./manifest";
import {canonicalizeNotionUrl} from "./urls";

type NotionCrawlerResponse = {
  schemaVersion?: unknown;
  manifestVersion?: unknown;
  extractorVersion?: unknown;
  sourceType?: unknown;
  canonicalUrl?: unknown;
  sourceRevision?: unknown;
  manifestHash?: unknown;
  itemCount?: unknown;
  totalBytes?: unknown;
  revisionId?: unknown;
  snapshotObjectPath?: unknown;
};

/**
 * Requests rendered public-Notion collection from the private Cloud Run app.
 * @param {string} projectId Project document ID
 * @param {string} rawUrl Configured public Notion URL
 * @param {string} bucketName Firebase Storage bucket name
 * @param {string} crawlerUrl Authenticated Cloud Run service URL
 * @return {Promise<SourceCollectionResult>} Persisted manifest metadata
 */
export async function collectNotionSource(
  projectId: string,
  rawUrl: string,
  bucketName: string,
  crawlerUrl: string
): Promise<SourceCollectionResult> {
  const canonicalUrl = canonicalizeNotionUrl(rawUrl);
  if (!canonicalUrl) {
    throw new SourceSyncWorkerError(
      "notion_url_invalid",
      "Notion 공개 페이지 URL 형식이 올바르지 않습니다.",
      false
    );
  }
  if (!crawlerUrl.trim()) {
    throw new SourceSyncWorkerError(
      "notion_crawler_not_configured",
      "Notion 수집 서비스가 아직 설정되지 않았습니다.",
      false
    );
  }
  try {
    const serviceUrl = new URL("/crawl", crawlerUrl).toString();
    const client = await new GoogleAuth().getIdTokenClient(crawlerUrl);
    const response = await client.request<NotionCrawlerResponse>({
      url: serviceUrl,
      method: "POST",
      timeout: SOURCE_SYNC_CONFIG.notion.maximumRuntimeMilliseconds,
      data: {
        projectId,
        url: canonicalUrl,
        bucketName,
        storagePrefix: SOURCE_SYNC_CONFIG.storagePrefix,
        policy: {
          schemaVersion: SOURCE_SYNC_CONFIG.schemaVersion,
          manifestVersion: SOURCE_SYNC_CONFIG.manifestVersion,
          extractorVersion: SOURCE_SYNC_CONFIG.extractorVersion,
          hashAlgorithm: SOURCE_SYNC_CONFIG.hashAlgorithm,
          ...SOURCE_SYNC_CONFIG.notion,
        },
      },
    });
    return parseCrawlerResponse(response.data, canonicalUrl);
  } catch (error) {
    if (error instanceof SourceSyncWorkerError) throw error;
    const status = responseStatus(error);
    const suffix = status === null ? "" : ` (HTTP ${status})`;
    throw new SourceSyncWorkerError(
      "notion_collection_failed",
      `공개 Notion 페이지를 수집하지 못했습니다.${suffix}`,
      true
    );
  }
}

/**
 * Extracts an HTTP status from a GoogleAuth/Gaxios response error.
 * @param {unknown} error Request failure
 * @return {number|null} HTTP status when available
 */
function responseStatus(error: unknown): number | null {
  if (typeof error !== "object" || error === null ||
      !("response" in error)) return null;
  const response = error.response;
  if (typeof response !== "object" || response === null ||
      !("status" in response)) return null;
  return typeof response.status === "number" ? response.status : null;
}

/**
 * Validates metadata returned by the trusted crawler boundary.
 * @param {NotionCrawlerResponse} value Crawler response body
 * @param {string} canonicalUrl Requested canonical URL
 * @return {SourceCollectionResult} Validated result
 */
function parseCrawlerResponse(
  value: NotionCrawlerResponse,
  canonicalUrl: string
): SourceCollectionResult {
  if (
    value.sourceType !== "notion" ||
    value.canonicalUrl !== canonicalUrl ||
    typeof value.manifestHash !== "string" ||
    !/^[a-f0-9]{64}$/.test(value.manifestHash) ||
    typeof value.revisionId !== "string" ||
    typeof value.snapshotObjectPath !== "string" ||
    typeof value.itemCount !== "number" ||
    typeof value.totalBytes !== "number" ||
    value.schemaVersion !== SOURCE_SYNC_CONFIG.schemaVersion ||
    value.manifestVersion !== SOURCE_SYNC_CONFIG.manifestVersion ||
    value.extractorVersion !== SOURCE_SYNC_CONFIG.extractorVersion
  ) {
    throw new SourceSyncWorkerError(
      "notion_crawler_response_invalid",
      "Notion 수집 서비스가 잘못된 결과를 반환했습니다.",
      true
    );
  }
  return {
    schemaVersion: value.schemaVersion,
    manifestVersion: value.manifestVersion,
    extractorVersion: value.extractorVersion,
    sourceType: "notion",
    canonicalUrl,
    sourceRevision: typeof value.sourceRevision === "string" ?
      value.sourceRevision : null,
    manifestHash: value.manifestHash,
    itemCount: value.itemCount,
    totalBytes: value.totalBytes,
    revisionId: value.revisionId,
    snapshotObjectPath: value.snapshotObjectPath,
  };
}
