import {FieldValue, getFirestore, Timestamp} from "firebase-admin/firestore";

export type ServerUsageCategory = "server_embedding" | "server_search";

export type ServerUsageInput = {
  uid: string;
  usageId: string;
  category: ServerUsageCategory;
  inputTokens: number;
  chunkCount: number;
  characterCount: number;
  projectId?: string;
  sessionId?: string;
  modelName: string;
};

/**
 * Stores idempotent server-side usage under the triggering user's account.
 * @param {ServerUsageInput} input Usage event and triggering user
 * @return {Promise<void>} Resolves after the event is stored
 */
export async function writeServerUsage(input: ServerUsageInput): Promise<void> {
  const db = getFirestore();
  const userReference = db.collection("users").doc(input.uid);
  const usageReference = userReference.collection("ai_usage")
    .doc(input.usageId);
  const projectReference = input.projectId ?
    db.collection("projects").doc(input.projectId) : null;

  await db.runTransaction(async (transaction) => {
    const existing = await transaction.get(usageReference);
    if (existing.exists) return;
    const projectSnapshot = projectReference ?
      await transaction.get(projectReference) : null;
    const projectName = projectSnapshot?.get("name");
    const tokens = Math.max(0, Math.floor(input.inputTokens));
    const now = Timestamp.now();
    transaction.set(usageReference, {
      modelName: input.modelName,
      keySource: "server",
      usageCategory: input.category,
      inputTokens: tokens,
      chunkCount: Math.max(0, Math.floor(input.chunkCount)),
      characterCount: Math.max(0, Math.floor(input.characterCount)),
      outputTokens: 0,
      thoughtsTokens: 0,
      totalTokens: tokens,
      projectId: input.projectId ?? null,
      projectName: typeof projectName === "string" ? projectName : null,
      sessionId: input.sessionId ?? null,
      sessionTitle: null,
      createdAt: now,
    });
    transaction.set(userReference, {
      totalAiInputTokens: increment(tokens),
      totalAiTokens: increment(tokens),
      [`${input.category}Tokens`]: increment(tokens),
      lastAiUsageAt: now,
    }, {merge: true});
  });
}

/**
 * Returns an atomic Firestore counter increment.
 * @param {number} value Counter delta
 * @return {FieldValue} Atomic counter transform
 */
function increment(value: number) {
  return FieldValue.increment(value);
}
