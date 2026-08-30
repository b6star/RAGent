import {FieldValue, Timestamp} from "firebase-admin/firestore";

import {SOURCE_SYNC_CONFIG} from "./config";

export const SOURCE_SYNC_STATUSES = [
  "idle",
  "queued",
  "checking",
  "changed",
  "ready",
  "error",
] as const;

export type SourceSyncStatus = typeof SOURCE_SYNC_STATUSES[number];

export const PUBLIC_SOURCE_TYPES = ["github", "notion"] as const;

export type PublicSourceType = typeof PUBLIC_SOURCE_TYPES[number];

export type FirestoreServerTime = Timestamp | FieldValue;

export type SourceSyncError = {
  code: string;
  message: string;
  retryable: boolean;
  occurredAt: FirestoreServerTime;
};

/**
 * Member-readable aggregate state.
 * Path: projects/{projectId}/sourceSync/status
 */
export type SourceSyncStatusDocument = {
  schemaVersion: number;
  status: SourceSyncStatus;
  activeRevisionId: string | null;
  lastRequestedAt: FirestoreServerTime | null;
  lastCheckedAt: FirestoreServerTime | null;
  lastChangedAt: FirestoreServerTime | null;
  lastCompletedAt: FirestoreServerTime | null;
  lastError: SourceSyncError | null;
  updatedAt: FirestoreServerTime;
};

/**
 * Server-only orchestration state. Never expose this document to clients.
 * Path: projects/{projectId}/sourceSync/control
 */
export type SourceSyncControlDocument = {
  schemaVersion: number;
  activeJobId: string | null;
  leaseOwner: string | null;
  leaseExpiresAt: FirestoreServerTime | null;
  throttleUntil: FirestoreServerTime | null;
  attempt: number;
  updatedAt: FirestoreServerTime;
};

/**
 * One document per configured source type.
 * Path: projects/{projectId}/sources/{github|notion}
 */
export type ProjectSourceDocument = {
  schemaVersion: number;
  sourceType: PublicSourceType;
  canonicalUrl: string;
  status: SourceSyncStatus;
  manifestHash: string | null;
  snapshotObjectPath: string | null;
  sourceRevision: string | null;
  itemCount: number;
  totalBytes: number;
  manifestVersion: number;
  extractorVersion: string;
  activeRevisionId: string | null;
  stagingRevisionId: string | null;
  lastCheckedAt: FirestoreServerTime | null;
  lastChangedAt: FirestoreServerTime | null;
  lastCompletedAt: FirestoreServerTime | null;
  lastError: SourceSyncError | null;
  createdAt: FirestoreServerTime;
  updatedAt: FirestoreServerTime;
};

const ALLOWED_STATUS_TRANSITIONS: Readonly<
  Record<SourceSyncStatus, readonly SourceSyncStatus[]>
> = Object.freeze({
  idle: ["queued"],
  queued: ["checking", "error"],
  checking: ["changed", "ready", "error"],
  changed: ["ready", "error"],
  ready: ["queued"],
  error: ["queued"],
});

/**
 * Validates a persisted synchronization status.
 * @param {unknown} value Candidate status value
 * @return {boolean} Whether the value is a supported status
 */
export function isSourceSyncStatus(
  value: unknown
): value is SourceSyncStatus {
  return typeof value === "string" &&
    SOURCE_SYNC_STATUSES.some((status) => status === value);
}

/**
 * Validates a configured public-source type.
 * @param {unknown} value Candidate source type
 * @return {boolean} Whether the value is a supported source type
 */
export function isPublicSourceType(
  value: unknown
): value is PublicSourceType {
  return typeof value === "string" &&
    PUBLIC_SOURCE_TYPES.some((sourceType) => sourceType === value);
}

/**
 * Ensures workers only perform an explicit state-machine transition.
 * Rewriting the same state is allowed for timestamp or progress updates.
 * @param {SourceSyncStatus} from Current status
 * @param {SourceSyncStatus} to Requested status
 * @return {boolean} Whether the transition is allowed
 */
export function canTransitionSourceSyncStatus(
  from: SourceSyncStatus,
  to: SourceSyncStatus
): boolean {
  return from === to || ALLOWED_STATUS_TRANSITIONS[from].includes(to);
}

/**
 * Creates the initial member-readable status document.
 * @param {FirestoreServerTime} serverTime Server timestamp sentinel
 * @return {SourceSyncStatusDocument} Initial status document
 */
export function createInitialSourceSyncStatus(
  serverTime: FirestoreServerTime
): SourceSyncStatusDocument {
  return {
    schemaVersion: SOURCE_SYNC_CONFIG.schemaVersion,
    status: "idle",
    activeRevisionId: null,
    lastRequestedAt: null,
    lastCheckedAt: null,
    lastChangedAt: null,
    lastCompletedAt: null,
    lastError: null,
    updatedAt: serverTime,
  };
}

/**
 * Creates the initial server-only orchestration document.
 * @param {FirestoreServerTime} serverTime Server timestamp sentinel
 * @return {SourceSyncControlDocument} Initial control document
 */
export function createInitialSourceSyncControl(
  serverTime: FirestoreServerTime
): SourceSyncControlDocument {
  return {
    schemaVersion: SOURCE_SYNC_CONFIG.schemaVersion,
    activeJobId: null,
    leaseOwner: null,
    leaseExpiresAt: null,
    throttleUntil: null,
    attempt: 0,
    updatedAt: serverTime,
  };
}

/**
 * Creates the initial per-source metadata document.
 * @param {PublicSourceType} sourceType GitHub or Notion
 * @param {string} canonicalUrl Validated canonical public URL
 * @param {FirestoreServerTime} serverTime Server timestamp sentinel
 * @return {ProjectSourceDocument} Initial source document
 */
export function createInitialProjectSource(
  sourceType: PublicSourceType,
  canonicalUrl: string,
  serverTime: FirestoreServerTime
): ProjectSourceDocument {
  return {
    schemaVersion: SOURCE_SYNC_CONFIG.schemaVersion,
    sourceType,
    canonicalUrl,
    status: "idle",
    manifestHash: null,
    snapshotObjectPath: null,
    sourceRevision: null,
    itemCount: 0,
    totalBytes: 0,
    manifestVersion: SOURCE_SYNC_CONFIG.manifestVersion,
    extractorVersion: SOURCE_SYNC_CONFIG.extractorVersion,
    activeRevisionId: null,
    stagingRevisionId: null,
    lastCheckedAt: null,
    lastChangedAt: null,
    lastCompletedAt: null,
    lastError: null,
    createdAt: serverTime,
    updatedAt: serverTime,
  };
}
