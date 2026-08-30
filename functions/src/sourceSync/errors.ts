import {SOURCE_SYNC_CONFIG} from "./config";

/** A bounded, retry-aware failure safe to persist for project members. */
export class SourceSyncWorkerError extends Error {
  readonly code: string;
  readonly retryable: boolean;

  /**
   * Creates a normalized worker error.
   * @param {string} code Stable machine-readable code
   * @param {string} message Member-safe error message
   * @param {boolean} retryable Whether another task attempt can help
   */
  constructor(code: string, message: string, retryable: boolean) {
    super(message);
    this.name = "SourceSyncWorkerError";
    this.code = code;
    this.retryable = retryable;
  }
}

/**
 * Converts unknown worker failures into bounded member-safe errors.
 * @param {unknown} error Worker failure
 * @return {SourceSyncWorkerError} Safe normalized error
 */
export function normalizeSourceSyncError(
  error: unknown
): SourceSyncWorkerError {
  if (error instanceof SourceSyncWorkerError) return error;
  const message = error instanceof Error ? error.message : String(error);
  return new SourceSyncWorkerError(
    "source_collection_failed",
    message.slice(0, SOURCE_SYNC_CONFIG.errorMessageMaximumLength),
    true
  );
}
