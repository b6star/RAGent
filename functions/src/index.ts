import {onCall, HttpsError} from "firebase-functions/v2/https";
import {defineSecret} from "firebase-functions/params";
import {setGlobalOptions} from "firebase-functions";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";
import {
  AiProvider,
  AiInlineAttachment,
  streamAiResponse,
  isAiProvider,
} from "./ai/providers";

admin.initializeApp();
const db = admin.firestore();

setGlobalOptions({maxInstances: 10, region: "asia-northeast3"});

const geminiKey = defineSecret("GEMINI_API_KEY");
const DEVELOPER_FREE_TOKEN_LIMIT = 300_000;
const DEVELOPER_MODEL_CATALOG: Record<AiProvider, readonly string[]> = {
  gemini: [
    "gemini-3.5-flash-lite",
    "gemini-3.7-flash",
    "gemini-3.1-pro-preview",
  ],
  openai: [
    "gpt-5.6-luna",
    "gpt-5.6-terra",
    "gpt-5.6-sol",
  ],
};

/**
 * Returns the first configured model for developer-key requests.
 * @param {AiProvider} provider Selected AI provider
 * @return {string} Developer model ID
 */
function getDeveloperModel(provider: AiProvider): string {
  return DEVELOPER_MODEL_CATALOG[provider][0];
}
// Personal-key usage is client-reported. This is a per-field sanity ceiling,
// not the developer-key free-token quota.
const MAX_CLIENT_REPORTED_TOKENS_PER_FIELD = 10_000_000;

type AiUsage = {
  inputTokens: number;
  outputTokens: number;
  thoughtsTokens: number;
  totalTokens: number;
};

type AiKeySource = "personal" | "developer";

type AiUsageWriteResult = {
  recorded: boolean;
  developerTokensRemaining?: number;
};

type ProviderSdkError = {
  status?: number;
  code?: number | string;
  type?: string;
  name?: string;
  message?: string;
  requestID?: string | null;
  error?: {
    type?: string;
    code?: number | string;
    status?: string;
    message?: string;
  };
};

type ProviderErrorReason =
  | "developer_api_key_invalid"
  | "developer_permission_denied"
  | "provider_quota_exceeded"
  | "rate_limited"
  | "model_not_found"
  | "invalid_request"
  | "content_blocked"
  | "timeout"
  | "provider_unavailable"
  | "empty_response";

/**
 * Returns the error sent when the per-user developer-key budget is spent.
 * @return {HttpsError} Normalized token-limit error
 */
function developerTokenLimitError(): HttpsError {
  return new HttpsError(
    "resource-exhausted",
    "개발자 API 무료 토큰을 모두 사용했습니다. 개인 API 키를 설정해 주세요.",
    {reason: "developer_token_limit", retryable: false}
  );
}

/**
 * Validates a client-reported token count.
 * @param {unknown} value Reported token count
 * @param {string} field Field name used in validation errors
 * @return {number} Validated non-negative integer
 */
function parseTokenCount(value: unknown, field: string): number {
  if (
    typeof value !== "number" ||
    !Number.isInteger(value) ||
    value < 0 ||
    value > MAX_CLIENT_REPORTED_TOKENS_PER_FIELD
  ) {
    throw new HttpsError("invalid-argument", `${field} 값이 올바르지 않습니다.`);
  }
  return value;
}

/**
 * Stores an idempotent AI usage event and updates user aggregates.
 * @param {string} uid Firebase user ID
 * @param {string} usageId Idempotency key for the usage event
 * @param {AiProvider} provider AI provider
 * @param {string} modelName Provider model name
 * @param {AiKeySource} keySource Personal or developer key source
 * @param {AiUsage} usage Token usage values
 * @param {string} projectId Optional project ID
 * @param {string} sessionId Optional chat session ID
 * @return {Promise<AiUsageWriteResult>} Usage write result
 */
async function writeAiUsage(
  uid: string,
  usageId: string,
  provider: AiProvider,
  modelName: string,
  keySource: AiKeySource,
  usage: AiUsage,
  projectId?: string,
  sessionId?: string
): Promise<AiUsageWriteResult> {
  const userRef = db.collection("users").doc(uid);
  const usageRef = userRef.collection("ai_usage").doc(usageId);

  return db.runTransaction(async (transaction) => {
    const existing = await transaction.get(usageRef);
    const userSnapshot = await transaction.get(userRef);
    const rawDeveloperTokens = userSnapshot.get("developerAiTotalTokens");
    const currentDeveloperTokens = typeof rawDeveloperTokens === "number" ?
      rawDeveloperTokens : 0;
    const developerTokensRemaining = Math.max(
      0,
      DEVELOPER_FREE_TOKEN_LIMIT - currentDeveloperTokens
    );

    if (existing.exists) {
      return {
        recorded: false,
        developerTokensRemaining,
      };
    }

    const projectSnapshot = projectId ? await transaction.get(
      db.collection("projects").doc(projectId)
    ) : null;
    const sessionSnapshot = projectId && sessionId ? await transaction.get(
      userRef.collection("ai_chats").doc(projectId)
        .collection("sessions").doc(sessionId)
    ) : null;
    const rawProjectName = projectSnapshot?.get("name");
    const rawSessionTitle = sessionSnapshot?.get("title");
    const projectName = typeof rawProjectName === "string" &&
      rawProjectName.trim() ? rawProjectName.trim() : null;
    const sessionTitle = typeof rawSessionTitle === "string" &&
      rawSessionTitle.trim() ? rawSessionTitle.trim() : null;

    transaction.set(usageRef, {
      provider,
      modelName,
      keySource,
      ...usage,
      projectId: projectId ?? null,
      projectName,
      sessionId: sessionId ?? null,
      sessionTitle,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    transaction.set(userRef, {
      totalAiInputTokens: admin.firestore.FieldValue.increment(
        usage.inputTokens
      ),
      totalAiOutputTokens: admin.firestore.FieldValue.increment(
        usage.outputTokens
      ),
      totalAiThoughtsTokens: admin.firestore.FieldValue.increment(
        usage.thoughtsTokens
      ),
      totalAiTokens: admin.firestore.FieldValue.increment(usage.totalTokens),
      [`${keySource}AiUsageCount`]: admin.firestore.FieldValue.increment(1),
      ...(keySource === "developer" ? {
        developerAiTotalTokens: admin.firestore.FieldValue.increment(
          usage.totalTokens
        ),
      } : {}),
      lastAiUsageAt: admin.firestore.FieldValue.serverTimestamp(),
    }, {merge: true});
    return {
      recorded: true,
      developerTokensRemaining: keySource === "developer" ?
        Math.max(
          0,
          DEVELOPER_FREE_TOKEN_LIMIT - currentDeveloperTokens -
          usage.totalTokens
        ) : undefined,
    };
  });
}

/**
 * Provider SDK 오류를 API 키가 노출되지 않는 Callable 오류로 변환한다.
 * @param {unknown} error SDK에서 전달된 오류
 * @param {AiProvider} provider 요청 Provider
 * @return {HttpsError} 클라이언트에 전달할 정규화된 오류
 */
function toProviderHttpsError(
  error: unknown,
  provider: AiProvider
): HttpsError {
  const sdkError = error as ProviderSdkError;
  const status = typeof sdkError?.status === "number" ? sdkError.status : null;
  const code = sdkError?.code ?? sdkError?.error?.code ??
    sdkError?.error?.status ?? sdkError?.error?.type ?? sdkError?.type ?? null;
  const hint = [
    code,
    sdkError?.name,
    sdkError?.message,
    sdkError?.error?.message,
  ].filter(Boolean).join(" ").toLowerCase();
  const requestId = sdkError?.requestID ?? null;

  // SDK 오류 객체 전체에는 요청 정보가 포함될 수 있으므로 그대로 기록하지 않는다.
  logger.error("AI provider request failed", {
    provider,
    status,
    code,
    requestId,
  });

  const providerError = (
    httpsCode: ConstructorParameters<typeof HttpsError>[0],
    message: string,
    reason: ProviderErrorReason,
    retryable: boolean
  ): HttpsError => new HttpsError(
    httpsCode,
    message,
    {reason, provider, retryable}
  );

  if (
    hint.includes("invalid_api_key") ||
    hint.includes("api key not valid") ||
    hint.includes("authentication") ||
    status === 401
  ) {
    return providerError(
      "unauthenticated",
      "개발자 API 키가 유효하지 않습니다.",
      "developer_api_key_invalid",
      false
    );
  }
  if (hint.includes("content_filter") || hint.includes("safety") ||
      hint.includes("blocked") || hint.includes("policy")) {
    return providerError(
      "failed-precondition",
      "안전 정책으로 인해 답변을 생성할 수 없습니다.",
      "content_blocked",
      false
    );
  }
  if (hint.includes("model_not_found") || status === 404) {
    return providerError(
      "not-found",
      "선택한 AI 모델을 찾을 수 없습니다.",
      "model_not_found",
      false
    );
  }
  if (
    hint.includes("insufficient_quota") ||
    hint.includes("quota_exceeded") ||
    hint.includes("daily quota") ||
    hint.includes("billing") ||
    hint.includes("credit") ||
    hint.includes("spend limit")
  ) {
    return providerError(
      "resource-exhausted",
      "개발자 AI Provider의 사용량 또는 결제 한도를 초과했습니다.",
      "provider_quota_exceeded",
      false
    );
  }
  if (hint.includes("rate_limit_exceeded") || status === 429) {
    return providerError(
      "resource-exhausted",
      "AI Provider 요청 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.",
      "rate_limited",
      true
    );
  }
  if (hint.includes("timeout") || status === 408 || status === 504) {
    return providerError(
      "deadline-exceeded",
      "AI Provider 응답 시간이 초과됐습니다.",
      "timeout",
      true
    );
  }
  if (hint.includes("empty_response")) {
    return providerError(
      "failed-precondition",
      "AI Provider가 표시할 수 있는 답변을 반환하지 않았습니다.",
      "empty_response",
      true
    );
  }
  if (status === 403) {
    return providerError(
      "permission-denied",
      "개발자 API 키에 선택한 모델을 사용할 권한이 없습니다.",
      "developer_permission_denied",
      false
    );
  }
  if (hint.includes("invalid_request") || hint.includes("invalid_prompt") ||
      hint.includes("invalid_argument")) {
    return providerError(
      "invalid-argument",
      "AI 요청 형식이나 선택한 모델을 확인해 주세요.",
      "invalid_request",
      false
    );
  }

  switch (status) {
  case 400:
  case 422:
    return new HttpsError(
      "invalid-argument",
      "AI 요청 형식이나 선택한 모델을 확인해 주세요.",
      {reason: "invalid_request", provider, retryable: false}
    );
  case 409:
    return providerError(
      "aborted",
      "AI Provider 요청이 충돌했습니다. 잠시 후 다시 시도해 주세요.",
      "provider_unavailable",
      true
    );
  case 500:
  case 501:
  case 502:
  case 503:
  case 529:
    return providerError(
      "unavailable",
      "AI Provider가 일시적으로 응답하지 않습니다.",
      "provider_unavailable",
      true
    );
  default:
    return providerError(
      "internal",
      "AI 응답을 생성하는 중 오류가 발생했습니다.",
      "provider_unavailable",
      true
    );
  }
}

/**
 * 하이브리드 방식: AI 호출 및 중요 통계(토큰)만 서버에서 처리
 */
export const askAi = onCall({
  secrets: [geminiKey],
  enforceAppCheck: true,
  // Callable requests are authenticated and checked inside
  // the Firebase trigger.
  invoker: "public",
}, async (request, response) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "로그인이 필요합니다.");
  }

  const prompt = typeof request.data?.prompt === "string" ?
    request.data.prompt.trim() : "";
  const providerValue = request.data?.provider ?? "gemini";
  const modelValue = typeof request.data?.model === "string" ?
    request.data.model.trim() : "";
  const usageIdValue = typeof request.data?.usageId === "string" ?
    request.data.usageId.trim() : "";
  const projectIdValue = typeof request.data?.projectId === "string" ?
    request.data.projectId.trim() : "";
  const sessionIdValue = typeof request.data?.sessionId === "string" ?
    request.data.sessionId.trim() : "";
  const attachments: AiInlineAttachment[] = Array.isArray(request.data?.attachments) ?
    request.data.attachments
      .filter((item: any) => typeof item?.mimeType === "string" && typeof item?.dataBase64 === "string")
      .map((item: any) => ({mimeType: item.mimeType, dataBase64: item.dataBase64})) : [];
  const encodedAttachmentBytes = attachments.reduce(
    (total, item) => total + Math.floor(item.dataBase64.length * 0.75), 0);
  if (encodedAttachmentBytes > 20 * 1024 * 1024) {
    throw new HttpsError("invalid-argument", "첨부 파일 전체 용량은 20MB 이하여야 합니다.");
  }
  if (!prompt) {
    throw new HttpsError("invalid-argument", "질문이 누락되었습니다.");
  }
  if (prompt.length > 20000) {
    throw new HttpsError("invalid-argument", "질문은 20,000자 이하여야 합니다.");
  }
  if (!isAiProvider(providerValue)) {
    throw new HttpsError("invalid-argument", "지원하지 않는 AI Provider입니다.");
  }
  const provider = providerValue;
  const developerModel = getDeveloperModel(provider);
  if (modelValue && modelValue !== developerModel) {
    throw new HttpsError(
      "invalid-argument",
      `개발자 키는 ${developerModel} 모델만 사용할 수 있습니다.`
    );
  }
  if (usageIdValue && !/^[A-Za-z0-9_-]{1,128}$/.test(usageIdValue)) {
    throw new HttpsError("invalid-argument", "사용량 ID 형식이 올바르지 않습니다.");
  }
  const uid = request.auth.uid;
  const apiKey = provider === "gemini" ? geminiKey.value() : "";
  if (!apiKey) {
    throw new HttpsError(
      "failed-precondition",
      `${provider} 개발자 API 키가 설정되지 않았습니다.`,
      {reason: "developer_key_missing", retryable: false}
    );
  }

  const userRef = db.collection("users").doc(uid);
  const userSnapshot = await userRef.get();
  const developerTokens = userSnapshot.get("developerAiTotalTokens");
  if (typeof developerTokens === "number" &&
      developerTokens >= DEVELOPER_FREE_TOKEN_LIMIT) {
    if (request.acceptsStreaming && response) {
      await response.sendChunk({
        type: "error",
        reason: "developer_token_limit",
      });
    }
    throw developerTokenLimitError();
  }

  logger.info("Starting AI response stream", {
    provider,
    uid,
    acceptsStreaming: request.acceptsStreaming,
  });

  let generation;
  try {
    generation = await streamAiResponse(
      provider,
      prompt,
      apiKey,
      async (delta) => {
        if (request.acceptsStreaming && response) {
          await response.sendChunk({type: "text-delta", delta});
        }
      },
      developerModel,
      response?.signal,
      attachments
    );
  } catch (error) {
    logger.error("Stream AI response failed", {provider, uid});
    throw toProviderHttpsError(error, provider);
  }

  try {
    const usageId = usageIdValue || userRef.collection("ai_usage").doc().id;
    const usageResult = await writeAiUsage(
      uid,
      usageId,
      provider,
      generation.modelName,
      "developer",
      generation,
      projectIdValue || undefined,
      sessionIdValue || undefined
    );
    return {
      ...generation,
      provider,
      keySource: "developer",
      developerTokensRemaining: usageResult.developerTokensRemaining ?? 0,
      status: "success",
    };
  } catch (error) {
    if (error instanceof HttpsError) throw error;
    logger.error("AI token usage update failed", {provider, uid});
    throw new HttpsError(
      "internal",
      "AI 사용량을 저장하지 못했습니다.",
      {reason: "usage_sync_failed", retryable: true}
    );
  }
});

/**
 * 개인 API 키로 Android에서 직접 호출한 AI 사용량을 서버에 저장한다.
 * Provider API 키와 프롬프트는 전달받지 않는다.
 */
export const recordPersonalAiUsage = onCall({
  enforceAppCheck: true,
  invoker: "public",
}, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "로그인이 필요합니다.");
  }

  const providerValue = request.data?.provider;
  const usageId = typeof request.data?.usageId === "string" ?
    request.data.usageId.trim() : "";
  const modelName = typeof request.data?.modelName === "string" ?
    request.data.modelName.trim() : "";
  const projectId = typeof request.data?.projectId === "string" ?
    request.data.projectId.trim() : "";
  const sessionId = typeof request.data?.sessionId === "string" ?
    request.data.sessionId.trim() : "";

  if (!isAiProvider(providerValue)) {
    throw new HttpsError("invalid-argument", "지원하지 않는 AI Provider입니다.");
  }
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(usageId)) {
    throw new HttpsError("invalid-argument", "사용량 ID 형식이 올바르지 않습니다.");
  }
  if (!modelName || modelName.length > 200) {
    throw new HttpsError("invalid-argument", "모델 이름이 올바르지 않습니다.");
  }
  if (projectId.length > 128 || sessionId.length > 128) {
    throw new HttpsError("invalid-argument", "대화 식별자가 올바르지 않습니다.");
  }

  const usage: AiUsage = {
    inputTokens: parseTokenCount(request.data?.inputTokens, "inputTokens"),
    outputTokens: parseTokenCount(request.data?.outputTokens, "outputTokens"),
    thoughtsTokens: parseTokenCount(
      request.data?.thoughtsTokens,
      "thoughtsTokens"
    ),
    totalTokens: parseTokenCount(request.data?.totalTokens, "totalTokens"),
  };

  let usageResult: AiUsageWriteResult;
  try {
    usageResult = await writeAiUsage(
      request.auth.uid,
      usageId,
      providerValue,
      modelName,
      "personal",
      usage,
      projectId || undefined,
      sessionId || undefined
    );
  } catch (error) {
    if (error instanceof HttpsError) throw error;
    logger.error("Personal AI usage update failed", {
      provider: providerValue,
      uid: request.auth.uid,
    });
    throw new HttpsError(
      "internal",
      "개인 API 사용량을 저장하지 못했습니다.",
      {reason: "usage_sync_failed", retryable: true}
    );
  }

  return {
    recorded: usageResult.recorded,
    provider: providerValue,
    keySource: "personal",
    totalTokens: usage.totalTokens,
    status: "success",
  };
});
