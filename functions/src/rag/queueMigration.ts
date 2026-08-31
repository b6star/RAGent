/* eslint-disable require-jsdoc, valid-jsdoc, max-len */

import {GoogleAuth} from "google-auth-library";
import {getFirestore, Timestamp} from "firebase-admin/firestore";
import {HttpsError, onCall} from "firebase-functions/v2/https";

import {SOURCE_SYNC_CONFIG} from "../sourceSync/config";

const LEGACY_REGION = "us-central1";
const LEGACY_FUNCTION = "embedRagRevisionTask";
const MIGRATION_DOCUMENT = "legacyEmbeddingQueuePurge";

/** Purges the legacy queue once for the whole Firebase project. */
export async function purgeLegacyEmbeddingQueueOnce(
  projectId: string
): Promise<boolean> {
  const db = getFirestore();
  void projectId;
  const migrationReference = db.collection("_migrations").doc(MIGRATION_DOCUMENT);
  const migrationSnapshot = await migrationReference.get();
  if (migrationSnapshot.exists) return false;

  const cloudProjectId = process.env.GCLOUD_PROJECT;
  if (!cloudProjectId) throw new Error("Firebase project ID is not configured");
  const cloudTasksQueue = [
    "https://cloudtasks.googleapis.com/v2/projects",
    cloudProjectId,
    "locations",
    LEGACY_REGION,
    "queues",
    LEGACY_FUNCTION + ":purge",
  ].join("/");
  const auth = new GoogleAuth({
    scopes: ["https://www.googleapis.com/auth/cloud-platform"],
  });
  const client = await auth.getClient();
  await client.request({url: cloudTasksQueue, method: "POST", data: {}});
  await migrationReference.set({
    purgedQueue: `${LEGACY_REGION}/${LEGACY_FUNCTION}`,
    completedAt: Timestamp.now(),
  });
  return true;
}

/** Purges the old us-central1 embedding queue exactly once. */
export const purgeLegacyEmbeddingQueue = onCall({
  region: SOURCE_SYNC_CONFIG.region,
  enforceAppCheck: true,
  invoker: "public",
}, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "로그인이 필요합니다.");
  }
  const projectId = typeof request.data?.projectId === "string" ?
    request.data.projectId.trim() : "";
  if (!projectId) {
    throw new HttpsError("invalid-argument", "프로젝트를 확인해 주세요.");
  }

  const db = getFirestore();
  const project = db.collection("projects").doc(projectId);
  const projectSnapshot = await project.get();
  if (!projectSnapshot.exists ||
      projectSnapshot.get("ownerId") !== request.auth.uid) {
    throw new HttpsError("permission-denied", "프로젝트 소유자만 실행할 수 있습니다.");
  }
  try {
    const purged = await purgeLegacyEmbeddingQueueOnce(projectId);
    return {status: purged ? "purged" : "already_purged"};
  } catch (error) {
    throw new HttpsError(
      "internal",
      error instanceof Error ? error.message : "Legacy queue purge failed"
    );
  }
  /*
  const [projectSnapshot, migrationSnapshot] = await Promise.all([
    project.get(),
    project.collection("rag").doc(MIGRATION_DOCUMENT).get(),
  ]);
  if (!projectSnapshot.exists ||
      projectSnapshot.get("ownerId") !== request.auth.uid) {
    throw new HttpsError("permission-denied", "프로젝트 소유자만 실행할 수 있습니다.");
  }
  if (migrationSnapshot.exists) {
    return {status: "already_purged"};
  }

  const cloudTasksQueue = [
    "https://cloudtasks.googleapis.com/v2/projects",
    process.env.GCLOUD_PROJECT,
    "locations",
    LEGACY_REGION,
    "queues",
    LEGACY_FUNCTION + ":purge",
  ].join("/");
  const auth = new GoogleAuth({
    scopes: ["https://www.googleapis.com/auth/cloud-platform"],
  });
  const client = await auth.getClient();
  await client.request({
    url: cloudTasksQueue,
    method: "POST",
    data: {},
  });

  await project.collection("rag").doc(MIGRATION_DOCUMENT).set({
    purgedQueue: `${LEGACY_REGION}/${LEGACY_FUNCTION}`,
    completedAt: Timestamp.now(),
  });
  return {status: "purged", queue: `${LEGACY_REGION}/${LEGACY_FUNCTION}`};
  */
});
