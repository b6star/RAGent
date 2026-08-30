import {randomUUID} from "node:crypto";

import {getFunctions} from "firebase-admin/functions";
import {
  DocumentData,
  DocumentSnapshot,
  getFirestore,
  Timestamp,
} from "firebase-admin/firestore";
import {HttpsError, onCall} from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";

import {SOURCE_SYNC_CONFIG} from "./config";
import {sourceSyncReferences} from "./firestore";
import {
  createInitialProjectSource,
  createInitialSourceSyncControl,
  createInitialSourceSyncStatus,
  isSourceSyncStatus,
  ProjectSourceDocument,
  PublicSourceType,
  SourceSyncStatus,
} from "./model";
import {
  canonicalizeGithubUrl,
  canonicalizeNotionUrl,
} from "./urls";

type RequestDisposition =
  "queued" | "in-progress" | "throttled" | "cleared";

export type SourceSyncRequestDecision = {
  disposition: RequestDisposition;
  retryAfterMilliseconds: number;
};

type QueuedSource = {
  sourceType: PublicSourceType;
  canonicalUrl: string;
  document: ProjectSourceDocument;
};

type CoordinatorTransactionResult = {
  disposition: RequestDisposition;
  status: SourceSyncStatus;
  jobId: string | null;
  retryAfterMilliseconds: number;
};

const PROJECT_ID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;

/**
 * Makes the pure throttle and lease decision used by the coordinator.
 * @param {number} now Current epoch milliseconds
 * @param {string|null} activeJobId Active job ID
 * @param {number|null} leaseExpiresAt Lease expiry epoch milliseconds
 * @param {number|null} throttleUntil Throttle expiry epoch milliseconds
 * @param {SourceSyncStatus} status Current aggregate status
 * @param {boolean} sourceChanged Whether a configured URL changed
 * @return {SourceSyncRequestDecision} Coordinator decision
 */
export function decideSourceSyncRequest(
  now: number,
  activeJobId: string | null,
  leaseExpiresAt: number | null,
  throttleUntil: number | null,
  status: SourceSyncStatus,
  sourceChanged: boolean
): SourceSyncRequestDecision {
  if (sourceChanged) {
    return {disposition: "queued", retryAfterMilliseconds: 0};
  }
  if (activeJobId && leaseExpiresAt !== null && leaseExpiresAt > now) {
    return {disposition: "in-progress", retryAfterMilliseconds: 0};
  }
  if (status === "ready" && throttleUntil !== null &&
      throttleUntil > now) {
    return {
      disposition: "throttled",
      retryAfterMilliseconds: throttleUntil - now,
    };
  }
  return {disposition: "queued", retryAfterMilliseconds: 0};
}

/**
 * Receives a project-entry synchronization request and queues one worker.
 */
export const requestSourceSync = onCall({
  enforceAppCheck: true,
  invoker: "public",
  region: SOURCE_SYNC_CONFIG.region,
}, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "로그인이 필요합니다.");
  }
  const projectId = typeof request.data?.projectId === "string" ?
    request.data.projectId.trim() : "";
  if (!PROJECT_ID_PATTERN.test(projectId)) {
    throw new HttpsError(
      "invalid-argument",
      "프로젝트 ID 형식이 올바르지 않습니다."
    );
  }

  const db = getFirestore();
  const references = sourceSyncReferences(db, projectId);
  const proposedJobId = randomUUID().replace(/-/g, "");
  const now = Timestamp.now();
  const result = await db.runTransaction(async (transaction) => {
    const member = references.project.collection("members")
      .doc(request.auth!.uid);
    const snapshots = await transaction.getAll(
      references.project,
      member,
      references.status,
      references.control,
      references.github,
      references.notion
    );
    const [
      projectSnapshot,
      memberSnapshot,
      statusSnapshot,
      controlSnapshot,
      githubSnapshot,
      notionSnapshot,
    ] = snapshots;
    if (!projectSnapshot.exists) {
      throw new HttpsError("not-found", "프로젝트를 찾을 수 없습니다.");
    }
    const ownerId = projectSnapshot.get("ownerId");
    if (ownerId !== request.auth!.uid && !memberSnapshot.exists) {
      throw new HttpsError(
        "permission-denied",
        "프로젝트 Source를 동기화할 권한이 없습니다."
      );
    }

    const githubUrl = canonicalizeConfiguredUrl(
      projectSnapshot.get("githubUrl"),
      canonicalizeGithubUrl,
      "GitHub"
    );
    const notionUrl = canonicalizeConfiguredUrl(
      projectSnapshot.get("docsUrl"),
      canonicalizeNotionUrl,
      "Notion"
    );
    const status = persistedStatus(statusSnapshot);
    const activeJobId = stringOrNull(controlSnapshot.get("activeJobId"));
    const leaseExpiresAt = timestampMillis(
      controlSnapshot.get("leaseExpiresAt")
    );
    const throttleUntil = timestampMillis(
      controlSnapshot.get("throttleUntil")
    );
    if (!githubUrl && !notionUrl) {
      const initialStatus = createInitialSourceSyncStatus(now);
      const initialControl = createInitialSourceSyncControl(now);
      transaction.set(references.status, initialStatus);
      transaction.set(references.control, initialControl);
      if (githubSnapshot.exists) transaction.delete(references.github);
      if (notionSnapshot.exists) transaction.delete(references.notion);
      return {
        disposition: "cleared",
        status: "idle",
        jobId: null,
        retryAfterMilliseconds: 0,
      } satisfies CoordinatorTransactionResult;
    }
    const sourceChanged = sourceConfigurationChanged(
      githubSnapshot,
      githubUrl
    ) || sourceConfigurationChanged(notionSnapshot, notionUrl);
    const decision = decideSourceSyncRequest(
      now.toMillis(),
      activeJobId,
      leaseExpiresAt,
      throttleUntil,
      status,
      sourceChanged
    );
    if (decision.disposition !== "queued") {
      return {
        disposition: decision.disposition,
        status,
        jobId: activeJobId,
        retryAfterMilliseconds: decision.retryAfterMilliseconds,
      } satisfies CoordinatorTransactionResult;
    }

    const sources = configuredSources(
      githubUrl,
      notionUrl,
      githubSnapshot,
      notionSnapshot,
      now
    );
    const initialStatus = createInitialSourceSyncStatus(now);
    transaction.set(references.status, {
      ...initialStatus,
      ...(statusSnapshot.exists ? statusSnapshot.data() : {}),
      schemaVersion: SOURCE_SYNC_CONFIG.schemaVersion,
      status: "queued",
      lastRequestedAt: now,
      lastError: null,
      updatedAt: now,
    });
    const initialControl = createInitialSourceSyncControl(now);
    const previousAttempt = numberOrZero(controlSnapshot.get("attempt"));
    transaction.set(references.control, {
      ...initialControl,
      activeJobId: proposedJobId,
      leaseOwner: `coordinator:${request.auth!.uid}`,
      leaseExpiresAt: Timestamp.fromMillis(
        now.toMillis() + SOURCE_SYNC_CONFIG.leaseMilliseconds
      ),
      throttleUntil: Timestamp.fromMillis(
        now.toMillis() + SOURCE_SYNC_CONFIG.throttleMilliseconds
      ),
      attempt: previousAttempt + 1,
      updatedAt: now,
    });
    for (const source of sources) {
      const reference = source.sourceType === "github" ?
        references.github : references.notion;
      transaction.set(reference, source.document);
    }
    if (!githubUrl && githubSnapshot.exists) {
      transaction.delete(references.github);
    }
    if (!notionUrl && notionSnapshot.exists) {
      transaction.delete(references.notion);
    }
    transaction.set(references.jobs.doc(proposedJobId), {
      schemaVersion: SOURCE_SYNC_CONFIG.schemaVersion,
      status: "queued",
      sourceTypes: sources.map((source) => source.sourceType),
      requestedBy: request.auth!.uid,
      requestedAt: now,
      startedAt: null,
      completedAt: null,
      updatedAt: now,
    });
    return {
      disposition: "queued",
      status: "queued",
      jobId: proposedJobId,
      retryAfterMilliseconds: 0,
    } satisfies CoordinatorTransactionResult;
  });

  if (result.disposition === "queued" && result.jobId) {
    try {
      const workerName = [
        "locations",
        SOURCE_SYNC_CONFIG.region,
        "functions",
        SOURCE_SYNC_CONFIG.worker.functionName,
      ].join("/");
      await getFunctions().taskQueue(workerName).enqueue({
        projectId,
        jobId: result.jobId,
      }, {
        id: result.jobId,
        dispatchDeadlineSeconds: SOURCE_SYNC_CONFIG.worker.timeoutSeconds,
      });
    } catch (error) {
      logger.error("Failed to enqueue source synchronization", {
        projectId,
        jobId: result.jobId,
        error,
      });
      await markQueueFailure(projectId, result.jobId);
      throw new HttpsError(
        "unavailable",
        "Source 동기화 작업을 등록하지 못했습니다. 잠시 후 다시 시도해 주세요."
      );
    }
  }

  return {
    ...result,
    policyVersion: SOURCE_SYNC_CONFIG.policyVersion,
  };
});

/**
 * Normalizes an optional stored source URL.
 * @param {unknown} value Stored project field
 * @param {Function} canonicalize URL validator
 * @param {string} label User-facing source label
 * @return {string|null} Canonical URL or null when blank
 */
function canonicalizeConfiguredUrl(
  value: unknown,
  canonicalize: (value: unknown) => string | null,
  label: string
): string | null {
  if (typeof value !== "string" || !value.trim()) return null;
  const canonicalUrl = canonicalize(value);
  if (!canonicalUrl) {
    throw new HttpsError(
      "failed-precondition",
      `${label} 공개 URL이 올바르지 않습니다.`
    );
  }
  return canonicalUrl;
}

/**
 * Creates queued source documents while preserving unchanged revisions.
 * @param {string|null} githubUrl Canonical GitHub URL
 * @param {string|null} notionUrl Canonical Notion URL
 * @param {DocumentSnapshot} githubSnapshot Existing GitHub metadata
 * @param {DocumentSnapshot} notionSnapshot Existing Notion metadata
 * @param {Timestamp} now Coordinator timestamp
 * @return {QueuedSource[]} Configured queued sources
 */
function configuredSources(
  githubUrl: string | null,
  notionUrl: string | null,
  githubSnapshot: DocumentSnapshot,
  notionSnapshot: DocumentSnapshot,
  now: Timestamp
): QueuedSource[] {
  const configured: Array<[
    PublicSourceType,
    string | null,
    DocumentSnapshot,
  ]> = [
    ["github", githubUrl, githubSnapshot],
    ["notion", notionUrl, notionSnapshot],
  ];
  return configured.flatMap(([sourceType, canonicalUrl, snapshot]) => {
    if (!canonicalUrl) return [];
    const initial = createInitialProjectSource(sourceType, canonicalUrl, now);
    const existing = snapshot.data();
    const unchangedUrl = existing?.canonicalUrl === canonicalUrl;
    return [{
      sourceType,
      canonicalUrl,
      document: {
        ...initial,
        ...(unchangedUrl ? persistedSourceFields(existing, initial) : {}),
        schemaVersion: SOURCE_SYNC_CONFIG.schemaVersion,
        sourceType,
        canonicalUrl,
        status: "queued",
        stagingRevisionId: null,
        lastError: null,
        updatedAt: now,
      },
    }];
  });
}

/**
 * Returns safe metadata fields for an unchanged source URL.
 * @param {DocumentData} data Existing source data
 * @param {ProjectSourceDocument} fallback Initial source defaults
 * @return {Partial<ProjectSourceDocument>} Preserved metadata
 */
function persistedSourceFields(
  data: DocumentData,
  fallback: ProjectSourceDocument
): Partial<ProjectSourceDocument> {
  return {
    manifestHash: stringOrNull(data.manifestHash),
    snapshotObjectPath: stringOrNull(data.snapshotObjectPath),
    sourceRevision: stringOrNull(data.sourceRevision),
    itemCount: numberOrZero(data.itemCount),
    totalBytes: numberOrZero(data.totalBytes),
    activeRevisionId: stringOrNull(data.activeRevisionId),
    lastCheckedAt: data.lastCheckedAt ?? fallback.lastCheckedAt,
    lastChangedAt: data.lastChangedAt ?? fallback.lastChangedAt,
    lastCompletedAt: data.lastCompletedAt ?? fallback.lastCompletedAt,
    createdAt: data.createdAt ?? fallback.createdAt,
  };
}

/**
 * Returns whether a source document and configured URL disagree.
 * @param {DocumentSnapshot} snapshot Existing source snapshot
 * @param {string|null} canonicalUrl Configured canonical URL
 * @return {boolean} Whether the source configuration changed
 */
function sourceConfigurationChanged(
  snapshot: DocumentSnapshot,
  canonicalUrl: string | null
): boolean {
  if (!canonicalUrl) return snapshot.exists;
  return !snapshot.exists || snapshot.get("canonicalUrl") !== canonicalUrl ||
    snapshot.get("manifestVersion") !==
      SOURCE_SYNC_CONFIG.manifestVersion ||
    snapshot.get("extractorVersion") !==
      SOURCE_SYNC_CONFIG.extractorVersion;
}

/**
 * Returns a valid persisted aggregate status or the initial state.
 * @param {DocumentSnapshot} snapshot Aggregate status snapshot
 * @return {SourceSyncStatus} Valid status
 */
function persistedStatus(snapshot: DocumentSnapshot): SourceSyncStatus {
  const value = snapshot.get("status");
  return isSourceSyncStatus(value) ? value : "idle";
}

/**
 * Converts Firestore Timestamp values to epoch milliseconds.
 * @param {unknown} value Candidate timestamp
 * @return {number|null} Epoch milliseconds or null
 */
function timestampMillis(value: unknown): number | null {
  return value instanceof Timestamp ? value.toMillis() : null;
}

/**
 * Returns a non-empty string or null.
 * @param {unknown} value Candidate string
 * @return {string|null} Normalized string
 */
function stringOrNull(value: unknown): string | null {
  return typeof value === "string" && value ? value : null;
}

/**
 * Returns a finite non-negative number or zero.
 * @param {unknown} value Candidate number
 * @return {number} Safe number
 */
function numberOrZero(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) && value >= 0 ?
    value : 0;
}

/**
 * Releases a lease when Cloud Tasks enqueue fails after transaction commit.
 * @param {string} projectId Project document ID
 * @param {string} jobId Failed job ID
 * @return {Promise<void>} Firestore update completion
 */
async function markQueueFailure(
  projectId: string,
  jobId: string
): Promise<void> {
  const db = getFirestore();
  const references = sourceSyncReferences(db, projectId);
  await db.runTransaction(async (transaction) => {
    const [control, status] = await transaction.getAll(
      references.control,
      references.status
    );
    if (control.get("activeJobId") !== jobId) return;
    const now = Timestamp.now();
    const error = {
      code: "queue_unavailable",
      message: "Source 동기화 작업을 등록하지 못했습니다.",
      retryable: true,
      occurredAt: now,
    };
    transaction.set(references.control, {
      activeJobId: null,
      leaseOwner: null,
      leaseExpiresAt: null,
      updatedAt: now,
    }, {merge: true});
    transaction.set(references.status, {
      ...(status.exists ? {} : createInitialSourceSyncStatus(now)),
      status: "error",
      lastError: error,
      updatedAt: now,
    }, {merge: true});
    transaction.set(references.jobs.doc(jobId), {
      status: "error",
      lastError: error,
      completedAt: now,
      updatedAt: now,
    }, {merge: true});
  });
}
