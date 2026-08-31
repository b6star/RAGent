export type CrawlerPolicy = {
  schemaVersion: number;
  manifestVersion: number;
  extractorVersion: string;
  hashAlgorithm: "sha256";
  maximumPages: number;
  maximumCrawlDepth: number;
  maximumPageBytes: number;
  maximumTotalBytes: number;
  maximumRuntimeMilliseconds: number;
  navigationTimeoutMilliseconds: number;
  renderWaitMilliseconds: number;
  expansionRounds: number;
  maximumExpandableElements: number;
  expansionClickTimeoutMilliseconds: number;
  expansionWaitMilliseconds: number;
  maximumScrollSteps: number;
  scrollWaitMilliseconds: number;
  stableScrollIterations: number;
  viewportWidth: number;
  viewportHeight: number;
};

export type CrawlRequest = {
  projectId: string;
  url: string;
  bucketName: string;
  storagePrefix: string;
  policy: CrawlerPolicy;
};

export class RequestValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "RequestValidationError";
  }
}

/** Validates the private service request sent by the coordinator worker. */
export function parseCrawlRequest(value: unknown): CrawlRequest {
  if (!isRecord(value)) {
    throw new RequestValidationError("Request body must be an object");
  }
  const projectId = requiredString(value.projectId, "projectId");
  const url = requiredString(value.url, "url");
  const bucketName = requiredString(value.bucketName, "bucketName");
  const storagePrefix = requiredString(value.storagePrefix, "storagePrefix");
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(projectId)) {
    throw new RequestValidationError("projectId is invalid");
  }
  if (storagePrefix.includes("..") || storagePrefix.includes("\\") ||
      storagePrefix.startsWith("/")) {
    throw new RequestValidationError("storagePrefix is invalid");
  }
  if (!isRecord(value.policy)) {
    throw new RequestValidationError("policy must be an object");
  }
  const policy = value.policy;
  if (policy.hashAlgorithm !== "sha256") {
    throw new RequestValidationError("Only sha256 is supported");
  }
  return {
    projectId,
    url,
    bucketName,
    storagePrefix,
    policy: {
      schemaVersion: positiveInteger(policy.schemaVersion, "schemaVersion"),
      manifestVersion: positiveInteger(
        policy.manifestVersion,
        "manifestVersion"
      ),
      extractorVersion: requiredString(
        policy.extractorVersion,
        "extractorVersion"
      ),
      hashAlgorithm: "sha256",
      maximumPages: positiveInteger(policy.maximumPages, "maximumPages"),
      maximumCrawlDepth: nonNegativeInteger(
        policy.maximumCrawlDepth,
        "maximumCrawlDepth"
      ),
      maximumPageBytes: positiveInteger(
        policy.maximumPageBytes,
        "maximumPageBytes"
      ),
      maximumTotalBytes: positiveInteger(
        policy.maximumTotalBytes,
        "maximumTotalBytes"
      ),
      maximumRuntimeMilliseconds: positiveInteger(
        policy.maximumRuntimeMilliseconds,
        "maximumRuntimeMilliseconds"
      ),
      navigationTimeoutMilliseconds: positiveInteger(
        policy.navigationTimeoutMilliseconds,
        "navigationTimeoutMilliseconds"
      ),
      renderWaitMilliseconds: nonNegativeInteger(
        policy.renderWaitMilliseconds,
        "renderWaitMilliseconds"
      ),
      expansionRounds: positiveInteger(
        policy.expansionRounds,
        "expansionRounds"
      ),
      maximumExpandableElements: positiveInteger(
        policy.maximumExpandableElements,
        "maximumExpandableElements"
      ),
      expansionClickTimeoutMilliseconds: positiveInteger(
        policy.expansionClickTimeoutMilliseconds,
        "expansionClickTimeoutMilliseconds"
      ),
      expansionWaitMilliseconds: nonNegativeInteger(
        policy.expansionWaitMilliseconds,
        "expansionWaitMilliseconds"
      ),
      maximumScrollSteps: positiveInteger(
        policy.maximumScrollSteps,
        "maximumScrollSteps"
      ),
      scrollWaitMilliseconds: nonNegativeInteger(
        policy.scrollWaitMilliseconds,
        "scrollWaitMilliseconds"
      ),
      stableScrollIterations: positiveInteger(
        policy.stableScrollIterations,
        "stableScrollIterations"
      ),
      viewportWidth: positiveInteger(
        policy.viewportWidth,
        "viewportWidth"
      ),
      viewportHeight: positiveInteger(
        policy.viewportHeight,
        "viewportHeight"
      ),
    },
  };
}

function requiredString(value: unknown, field: string): string {
  if (typeof value !== "string" || !value.trim()) {
    throw new RequestValidationError(`${field} must be a non-empty string`);
  }
  return value.trim();
}

function positiveInteger(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value <= 0) {
    throw new RequestValidationError(`${field} must be a positive integer`);
  }
  return value;
}

function nonNegativeInteger(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) {
    throw new RequestValidationError(
      `${field} must be a non-negative integer`
    );
  }
  return value;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
