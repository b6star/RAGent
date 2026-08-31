import {
  DocumentData,
  DocumentReference,
  getFirestore,
  Timestamp,
} from "firebase-admin/firestore";
import {getStorage} from "firebase-admin/storage";
import {defineString} from "firebase-functions/params";
import * as logger from "firebase-functions/logger";
import {onTaskDispatched} from "firebase-functions/v2/tasks";

import {SOURCE_SYNC_CONFIG} from "./config";
import {normalizeSourceSyncError} from "./errors";
import {sourceSyncReferences} from "./firestore";
import {collectGithubSource} from "./github";
import {normalizeSnapshot, NormalizedDocument} from "./document";
import {
  compareSnapshots,
  decodeSourceSnapshot,
  sha256,
  SourceCollectionResult,
  SourceSnapshot,
} from "./manifest";
import {
  createInitialSourceSyncStatus,
  PublicSourceType,
} from "./model";
import {collectNotionSource} from "./notion";
import {stageRagRevision} from "../rag/pipeline";

type ClaimedSource = {
  sourceType: PublicSourceType;
  canonicalUrl: string;
};

type ClaimedJob = {
  projectId: string;
  jobId: string;
  triggeredBy: string;
  sources: ClaimedSource[];
};

const MAX_CHANGE_KEYS = 1000;

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
    const [control, github, notion, job] = await transaction.getAll(
      references.control,
      references.github,
      references.notion,
      references.jobs.doc(jobId)
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
    const triggeredBy = job.get("requestedBy");
    if (typeof triggeredBy !== "string" || !triggeredBy) return null;
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
    return {projectId, jobId, triggeredBy, sources};
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
  const currentSources = await Promise.all(job.sources.map(async (source) => {
    const reference = source.sourceType === "github" ?
      references.github : references.notion;
    return {source, snapshot: await reference.get()};
  }));
  const changes = new Map<
    PublicSourceType,
    ReturnType<typeof compareSnapshots>
  >();
  const snapshots = new Map<PublicSourceType, SourceSnapshot>();
  for (const result of results) {
    const current = currentSources.find((entry) =>
      entry.source.sourceType === result.sourceType)?.snapshot;
    const previousPath = current?.get("snapshotObjectPath");
    const previous = typeof previousPath === "string" ?
      await downloadSnapshot(getStorage().bucket(), previousPath) : null;
    const next = await downloadSnapshot(
      getStorage().bucket(), result.snapshotObjectPath
    );
    if (next) {
      changes.set(result.sourceType, compareSnapshots(previous, next));
      snapshots.set(result.sourceType, next);
    }
  }
  const staged = await db.runTransaction(async (transaction) => {
    const [control, status, github, notion] = await transaction.getAll(
      references.control,
      references.status,
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
      const changed = current.get("manifestHash") !== result.manifestHash ||
        current.get("extractorVersion") !== result.extractorVersion;
      if (changed) changedTypes.push(result.sourceType);
      const documentChanges = changes.get(result.sourceType);
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
        documentChanges: documentChanges ?
          summarizeChanges(documentChanges) : null,
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
      activeRevisionId: changedTypes.length ?
        status.get("activeRevisionId") ?? null : aggregateRevisionId,
      lastCheckedAt: now,
      lastCompletedAt: changedTypes.length ? null : now,
      lastError: null,
      updatedAt: now,
    }, {merge: true});
    transaction.set(references.jobs.doc(job.jobId), {
      status: changedTypes.length ? "changed" : "ready",
      aggregateRevisionId,
      changedSourceTypes: changedTypes,
      documentChanges: Object.fromEntries(
        [...changes.entries()].map(([type, value]) => [
          type, summarizeChanges(value),
        ])
      ),
      updatedAt: now,
    }, {merge: true});
    return {changedTypes, aggregateRevisionId};
  });
  if (!staged) return;
  await persistNormalizedDocuments(
    job.projectId, snapshots, changes, "staging"
  );
  // Write all normalized documents before flipping the active revision.
  // This keeps the previous active revision visible if promotion fails.
  await promoteNormalizedDocuments(job.projectId, snapshots, changes);
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
  await stageRagRevision(
    job.projectId,
    Object.fromEntries(results.map((result) => [
      result.sourceType,
      result.revisionId,
    ])),
    job.triggeredBy
  );
}

/**
 * Persists changed Documents under the source metadata document.
 * @param {string} projectId Project document ID
 * @param {Map} snapshots Newly collected snapshots
 * @param {Map} changes Document change sets
 * @param {string} revisionState Document revision state
 * @return {Promise<void>} Persistence completion
 */
async function persistNormalizedDocuments(
  projectId: string,
  snapshots: Map<PublicSourceType, SourceSnapshot>,
  changes: Map<PublicSourceType, ReturnType<typeof compareSnapshots>>,
  revisionState: "staging" | "active"
): Promise<void> {
  const db = getFirestore();
  const references = sourceSyncReferences(db, projectId);
  for (const [sourceType, snapshot] of snapshots) {
    const changeSet = changes.get(sourceType);
    if (!changeSet) continue;
    const byKey = new Map(normalizeSnapshot(snapshot).map((doc) => [
      doc.metadata.itemKey,
      doc,
    ]));
    const changedKeys = new Set([
      ...changeSet.added,
      ...changeSet.modified,
    ]);
    const documents = [...changedKeys]
      .map((key) => byKey.get(key))
      .filter((doc): doc is NormalizedDocument => doc !== undefined);
    const source = sourceType === "github" ?
      references.github : references.notion;
    const existingDocuments = await source.collection("documents").get();
    const hasActiveDocuments = existingDocuments.docs.some((document) =>
      document.get("status") === "active" &&
      document.get("revisionState") === "active"
    );
    const needsInitialBackfill = !hasActiveDocuments;
    const writes: Array<{
      reference: DocumentReference;
      data: DocumentData;
    }> = [];
    const documentsToPersist = needsInitialBackfill ?
      normalizeSnapshot(snapshot) : documents;
    for (const document of documentsToPersist) {
      writes.push({
        reference: source.collection("documents").doc(document.documentId),
        data: {
          ...document,
          status: "active",
          revisionState,
          updatedAt: Timestamp.now(),
        },
      });
    }
    for (const key of changeSet.deleted) {
      const sourceId = sha256(
        `${sourceType}\u0000${snapshot.canonicalUrl}`
      );
      const documentId = sha256(`${sourceId}\u0000${key}`);
      writes.push({
        reference: source.collection("documents").doc(documentId),
        data: {
          documentId,
          sourceType,
          status: "deleted",
          revisionState,
          sourceRevision: snapshot.sourceRevision,
          updatedAt: Timestamp.now(),
        },
      });
    }
    for (let index = 0; index < writes.length; index += 450) {
      const batch = db.batch();
      writes.slice(index, index + 450).forEach((write) => {
        batch.set(write.reference, write.data, {merge: true});
      });
      await batch.commit();
    }
  }
}

/**
 * Promotes staged normalized Documents after the source revision is committed.
 * @param {string} projectId Project document ID
 * @param {Map} snapshots Newly collected snapshots
 * @param {Map} changes Document change sets
 * @return {Promise<void>} Promotion completion
 */
async function promoteNormalizedDocuments(
  projectId: string,
  snapshots: Map<PublicSourceType, SourceSnapshot>,
  changes: Map<PublicSourceType, ReturnType<typeof compareSnapshots>>
): Promise<void> {
  await persistNormalizedDocuments(projectId, snapshots, changes, "active");
}

/**
 * Downloads and decodes a stored source snapshot.
 * @param {object} bucket Storage bucket
 * @param {string} objectPath Snapshot object path
 * @return {Promise<SourceSnapshot|null>} Decoded snapshot or null
 */
async function downloadSnapshot(
  bucket: ReturnType<ReturnType<typeof getStorage>["bucket"]>,
  objectPath: string
): Promise<SourceSnapshot | null> {
  try {
    const [downloaded] = await bucket.file(objectPath).download();
    return await decodeSourceSnapshot(downloaded);
  } catch (error) {
    logger.warn("Source manifest comparison skipped snapshot", {
      objectPath,
      error,
    });
    return null;
  }
}

/**
 * Limits persisted change lists while preserving complete counts.
 * @param {object} changes Computed manifest changes
 * @return {object} Persistable change summary
 */
function summarizeChanges(changes: ReturnType<typeof compareSnapshots>) {
  return {
    added: changes.added.slice(0, MAX_CHANGE_KEYS),
    modified: changes.modified.slice(0, MAX_CHANGE_KEYS),
    deleted: changes.deleted.slice(0, MAX_CHANGE_KEYS),
    addedCount: changes.added.length,
    modifiedCount: changes.modified.length,
    deletedCount: changes.deleted.length,
  };
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
    const persistedError = {
      code: error.code,
      message: error.message,
      retryable: error.retryable,
      occurredAt: now,
    };
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
    const persistedError = {
      code: error.code,
      message: error.message,
      retryable: error.retryable,
      occurredAt: now,
    };
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
