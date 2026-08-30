/**
 * Single source of truth for public-source synchronization policy.
 *
 * Android receives user-facing state from Firestore and must not duplicate
 * these server enforcement values.
 */
export const SOURCE_SYNC_CONFIG = Object.freeze({
  region: "asia-northeast3",
  schemaVersion: 1,
  policyVersion: "public-link-v1",
  hashAlgorithm: "sha256",
  manifestVersion: 1,
  extractorVersion: "public-link-v1",
  throttleMilliseconds: 15 * 60 * 1000,
  leaseMilliseconds: 30 * 60 * 1000,
  maximumAttempts: 3,
  errorCodeMaximumLength: 80,
  errorMessageMaximumLength: 500,
  storagePrefix: "source-sync",
  worker: Object.freeze({
    functionName: "syncPublicSources",
    timeoutSeconds: 30 * 60,
    memory: "2GiB" as const,
    maximumInstances: 2,
    maximumConcurrentDispatches: 2,
    maximumDispatchesPerSecond: 2,
    minimumBackoffSeconds: 30,
    maximumBackoffSeconds: 5 * 60,
  }),
  github: Object.freeze({
    maximumFiles: 20_000,
    maximumFileBytes: 2 * 1024 * 1024,
    maximumTotalBytes: 100 * 1024 * 1024,
    maximumRuntimeMilliseconds: 20 * 60 * 1000,
  }),
  notion: Object.freeze({
    maximumPages: 20,
    maximumCrawlDepth: 2,
    maximumPageBytes: 2 * 1024 * 1024,
    maximumTotalBytes: 50 * 1024 * 1024,
    maximumRuntimeMilliseconds: 20 * 60 * 1000,
    navigationTimeoutMilliseconds: 60 * 1000,
    renderWaitMilliseconds: 3 * 1000,
    expansionRounds: 8,
    maximumExpandableElements: 100,
    expansionClickTimeoutMilliseconds: 1500,
    expansionWaitMilliseconds: 500,
    maximumScrollSteps: 40,
    scrollWaitMilliseconds: 200,
    stableScrollIterations: 3,
    viewportWidth: 1440,
    viewportHeight: 1000,
  }),
});

export type SourceSyncConfig = typeof SOURCE_SYNC_CONFIG;
