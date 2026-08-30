import {getFirestore, Timestamp} from "firebase-admin/firestore";
import {getStorage} from "firebase-admin/storage";
import {defineString} from "firebase-functions/params";
import * as logger from "firebase-functions/logger";
import {onTaskDispatched} from "firebase-functions/v2/tasks";

import {SOURCE_SYNC_CONFIG} from "./config";
import {normalizeSourceSyncError} from "./errors";
import {sourceSyncReferences} from "./firestore";
import {collectGithubSource} from "./github";
import {sha256, SourceCollectionResult} from "./manifest";
import {
  createInitialSourceSyncStatus,
  PublicSourceType,
} from "./model";
import {collectNotionSource} from "./notion";

type ClaimedSource = {
  sourceType: PublicSourceType;
  canonicalUrl: string;
};

type ClaimedJob = {
  projectId: string;
  jobId: string;
  sources: ClaimedSource[];
};

const notionCrawlerUrl = defineString("NOTION_CRAWLER_URL", {
  description: "Private Cloud Run URL for the RAGent Notion crawler",
});

/**
 * Runs the queued public-source collectors and commits one active revision.
 */
export const syncPublicSources = onTaskDispatched({
  region: SOURCE_SYNC_CONFIG.region,
  memory: SOURCE_SYNC_CONFIG.worker.memory,
  timeoutSeconds: SOURCE_SYNC_CONFIG.worker.timeoutSeconds,
  maxInstances: SOURCE_SYNC_CONFIG.worker.maximumInstances,
  concurrency: 1,
  retryConfig: {
    maxAttempts: SOURCE_SYNC_CONFIG.maximumAttempts,
    minBackoffSeconds: SOURCE_SYNC_CONFIG.worker.minimumBackoffSeconds,
    maxBackoffSeconds: SOURCE_SYNC_CONFIG.worker.maximumBackoffSeconds,
  },
  rateLimits: {
    maxConcurrentDispatches:
      SOURCE_SYNC_CONFIG.worker.maximumConcurrentDispatches,
    maxDispatchesPerSecond:
      SOURCE_SYNC_CONFIG.worker.maximumDispatchesPerSecond,
  },
}, async (request) => {
  const projectId = typeof request.data?.projectId === "string" ?
    request.data.projectId : "";
  const jobId = typeof request.data?.jobId === "string" ?
    request.data.jobId : "";
  if (!projectId || !jobId) {
    logger.error("Ignoring malformed source synchronization task");
    return;
  }
  logger.info("syncPublicSources invoked", {
    projectId,
    jobId,
    retryCount: request.retryCount,
  });
  const claimedJob = await claimJob(projectId, jobId);
  if (!claimedJob) {
    logger.info("Ignoring stale source synchronization task", {
      projectId,
      jobId,
    });
    return;
  }

  try {
    const bucket = getStorage().bucket();
    const results = await Promise.all(claimedJob.sources.map(async (source) => {
      logger.info("Source collection started", {
        projectId,
        jobId,
        sourceType: source.sourceType,
      });
      try {
        const result = source.sourceType === "github" ?
          await collectGithubSource(projectId, source.canonicalUrl, bucket) :
          await collectNotionSource(
            projectId,
            source.canonicalUrl,
            bucket.name,
            notionCrawlerUrl.value()
          );
        logger.info("Source collection completed", {
          projectId,
          jobId,
          sourceType: source.sourceType,
          itemCount: result.itemCount,
          manifestHash: result.manifestHash,
        });
        return result;
      } catch (error) {
        const errorMessage = error instanceof Error ?
          error.message : String(error);
        logger.error(
          `Source collection failed sourceType=${source.sourceType}: ` +
          errorMessage,
          {
            projectId,
            jobId,
            sourceType: source.sourceType,
            errorMessage,
          }
        );
        throw error;
      }
    }));
    await completeJob(claimedJob, results);
    logger.info("Source synchronization completed", {
      projectId,
      jobId,
      sourceTypes: results.map((result) => result.sourceType),
    });
  } catch (error) {
    const normalized = normalizeSourceSyncError(error);
    const finalAttempt = !normalized.retryable ||
      request.retryCount >= SOURCE_SYNC_CONFIG.maximumAttempts - 1;
    logger.error("Source synchronization attempt failed", {
      projectId,
      jobId,
      code: normalized.code,
      retryable: normalized.retryable,
      retryCount: request.retryCount,
    });
    if (finalAttempt) {
      await failJob(claimedJob, normalized);
      return;
    }
    await renewFailedAttempt(claimedJob, normalized);
    throw normalized;
  }
});

/**
 * Claims an active job and moves configured sources to checking.
 * @param {string} projectId Project document ID
 * @param {string} jobId Queued job ID
 * @return {Promise<ClaimedJob|null>} Claimed work or null when stale
 */
async function claimJob(
  projectId: string,
  jobId: string
): Promise<ClaimedJob | null> {
  const db = getFirestore();
  const references = sourceSyncReferences(db, projectId);
  return db.runTransaction(async (transaction) => {
    const [control, github, notion] = await transaction.getAll(
      references.control,
      references.github,
      references.notion
    );
    if (control.get("activeJobId") !== jobId) return null;
    const sources: ClaimedSource[] = [];
    for (const [sourceType, snapshot] of [
      ["github", github],
      ["notion", notion],
    ] as const) {
      const canonicalUrl = snapshot.get("canonicalUrl");
      if (snapshot.exists && typeof canonicalUrl === "string" &&
          canonicalUrl) {
        sources.push({sourceType, canonicalUrl});
      }
    }
    if (!sources.length) return null;
    const now = Timestamp.now();
    transaction.set(references.control, {
      leaseOwner: `worker:${jobId}`,
      leaseExpiresAt: Timestamp.fromMillis(
        now.toMillis() + SOURCE_SYNC_CONFIG.leaseMilliseconds
      ),
      updatedAt: now,
    }, {merge: true});
    transaction.set(references.status, {
      status: "checking",
      lastError: null,
      updatedAt: now,
    }, {merge: true});
    for (const source of sources) {
      transaction.set(
        source.sourceType === "github" ? references.github :
          references.notion,
        {status: "checking", lastError: null, updatedAt: now},
        {merge: true}
      );
    }
    transaction.set(references.jobs.doc(jobId), {
      status: "checking",
      startedAt: now,
      updatedAt: now,
    }, {merge: true});
    return {projectId, jobId, sources};
  });
}

/**
 * Persists manifests, records changed state, and promotes the revision.
 * @param {ClaimedJob} job Claimed synchronization job
 * @param {SourceCollectionResult[]} results Collector results
 * @return {Promise<void>} Firestore commit completion
 */
async function completeJob(
  job: ClaimedJob,
  results: SourceCollectionResult[]
): Promise<void> {
  const db = getFirestore();
  const references = sourceSyncReferences(db, job.projectId);
  const staged = await db.runTransaction(async (transaction) => {
    const [control, github, notion] = await transaction.getAll(
      references.control,
      references.github,
      references.notion
    );
    if (control.get("activeJobId") !== job.jobId) return null;
    const now = Timestamp.now();
    const changedTypes: PublicSourceType[] = [];
    for (const result of results) {
      const reference = result.sourceType === "github" ?
        references.github : references.notion;
      const current = result.sourceType === "github" ? github : notion;
      const changed = current.get("manifestHash") !== result.manifestHash;
      if (changed) changedTypes.push(result.sourceType);
      transaction.set(reference, {
        manifestHash: result.manifestHash,
        snapshotObjectPath: result.snapshotObjectPath,
        sourceRevision: result.sourceRevision,
        itemCount: result.itemCount,
        totalBytes: result.totalBytes,
        manifestVersion: result.manifestVersion,
        extractorVersion: result.extractorVersion,
        status: changed ? "changed" : "ready",
        stagingRevisionId: changed ? result.revisionId : null,
        activeRevisionId: changed ? current.get("activeRevisionId") ?? null :
          result.revisionId,
        lastCheckedAt: now,
        lastCompletedAt: changed ? current.get("lastCompletedAt") ?? null : now,
        lastError: null,
        updatedAt: now,
      }, {merge: true});
    }
    const aggregateRevisionId = aggregateRevision(results);
    transaction.set(references.status, {
      status: changedTypes.length ? "changed" : "ready",
      activeRevisionId: aggregateRevisionId,
      lastCheckedAt: now,
      lastCompletedAt: changedTypes.length ? null : now,
      lastError: null,
      updatedAt: now,
    }, {merge: true});
    transaction.set(references.jobs.doc(job.jobId), {
      status: changedTypes.length ? "changed" : "ready",
      aggregateRevisionId,
      changedSourceTypes: changedTypes,
      updatedAt: now,
    }, {merge: true});
    return {changedTypes, aggregateRevisionId};
  });
  if (!staged) return;
  await db.runTransaction(async (transaction) => {
    const control = await transaction.get(references.control);
    if (control.get("activeJobId") !== job.jobId) return;
    const now = Timestamp.now();
    for (const result of results) {
      const changed = staged.changedTypes.includes(result.sourceType);
      transaction.set(
        result.sourceType === "github" ? references.github :
          references.notion,
        {
          status: "ready",
          activeRevisionId: result.revisionId,
          stagingRevisionId: null,
          ...(changed ? {lastChangedAt: now} : {}),
          lastCompletedAt: now,
          updatedAt: now,
        },
        {merge: true}
      );
    }
    transaction.set(references.status, {
      status: "ready",
      activeRevisionId: staged.aggregateRevisionId,
      ...(staged.changedTypes.length ? {lastChangedAt: now} : {}),
      lastCompletedAt: now,
      updatedAt: now,
    }, {merge: true});
    transaction.set(references.control, {
      activeJobId: null,
      leaseOwner: null,
      leaseExpiresAt: null,
      throttleUntil: Timestamp.fromMillis(
        now.toMillis() + SOURCE_SYNC_CONFIG.throttleMilliseconds
      ),
      updatedAt: now,
    }, {merge: true});
    transaction.set(references.jobs.doc(job.jobId), {
      status: "ready",
      completedAt: now,
      updatedAt: now,
    }, {merge: true});
  });
}

/**
 * Keeps the lease alive while Cloud Tasks schedules a retry.
 * @param {ClaimedJob} job Claimed synchronization job
 * @param {object} error Retryable member-safe error
 * @return {Promise<void>} Firestore update completion
 */
async function renewFailedAttempt(
  job: ClaimedJob,
  error: {code: string; message: string; retryable: boolean}
): Promise<void> {
  const db = getFirestore();
  const references = sourceSyncReferences(db, job.projectId);
  await db.runTransaction(async (transaction) => {
    const control = await transaction.get(references.control);
    if (control.get("activeJobId") !== job.jobId) return;
    const now = Timestamp.now();
    const persistedError = {...error, occurredAt: now};
    transaction.set(references.control, {
      leaseExpiresAt: Timestamp.fromMillis(
        now.toMillis() + SOURCE_SYNC_CONFIG.leaseMilliseconds
      ),
      updatedAt: now,
    }, {merge: true});
    transaction.set(references.jobs.doc(job.jobId), {
      status: "retrying",
      lastError: persistedError,
      updatedAt: now,
    }, {merge: true});
  });
}

/**
 * Marks a terminal failure and releases the active lease.
 * @param {ClaimedJob} job Claimed synchronization job
 * @param {object} error Terminal member-safe error
 * @return {Promise<void>} Firestore update completion
 */
async function failJob(
  job: ClaimedJob,
  error: {code: string; message: string; retryable: boolean}
): Promise<void> {
  const db = getFirestore();
  const references = sourceSyncReferences(db, job.projectId);
  await db.runTransaction(async (transaction) => {
    const [control, status] = await transaction.getAll(
      references.control,
      references.status
    );
    if (control.get("activeJobId") !== job.jobId) return;
    const now = Timestamp.now();
    const persistedError = {...error, occurredAt: now};
    transaction.set(references.status, {
      ...(status.exists ? {} : createInitialSourceSyncStatus(now)),
      status: "error",
      lastError: persistedError,
      updatedAt: now,
    }, {merge: true});
    for (const source of job.sources) {
      transaction.set(
        source.sourceType === "github" ? references.github :
          references.notion,
        {
          status: "error",
          stagingRevisionId: null,
          lastError: persistedError,
          updatedAt: now,
        },
        {merge: true}
      );
    }
    transaction.set(references.control, {
      activeJobId: null,
      leaseOwner: null,
      leaseExpiresAt: null,
      updatedAt: now,
    }, {merge: true});
    transaction.set(references.jobs.doc(job.jobId), {
      status: "error",
      lastError: persistedError,
      completedAt: now,
      updatedAt: now,
    }, {merge: true});
  });
}

/**
 * Creates a deterministic aggregate revision from source manifests.
 * @param {SourceCollectionResult[]} results Collector results
 * @return {string} Combined revision ID
 */
function aggregateRevision(results: SourceCollectionResult[]): string {
  const input = [...results]
    .sort((left, right) => left.sourceType.localeCompare(right.sourceType))
    .map((result) => `${result.sourceType}\u0000${result.manifestHash}`)
    .join("\n");
  return `combined-${sha256(input)}`;
}
