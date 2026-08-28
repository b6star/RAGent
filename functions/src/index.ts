import {onCall, HttpsError} from "firebase-functions/v2/https";
import {defineSecret} from "firebase-functions/params";
import {setGlobalOptions} from "firebase-functions";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";
import {
  AiProvider,
  streamAiResponse,
  isAiProvider,
} from "./ai/providers";

admin.initializeApp();
const db = admin.firestore();

setGlobalOptions({maxInstances: 10, region: "asia-northeast3"});

const geminiKey = defineSecret("GEMINI_API_KEY");

type ProviderSdkError = {
  status?: number;
  code?: number | string;
  error?: {
    type?: string;
  };
};

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
  const code = sdkError?.code ?? sdkError?.error?.type ?? null;

  // SDK 오류 객체 전체에는 요청 정보가 포함될 수 있으므로 그대로 기록하지 않는다.
  logger.error("AI provider request failed", {provider, status, code});

  switch (status) {
  case 400:
  case 404:
    return new HttpsError(
      "invalid-argument",
      "AI 요청 형식이나 선택한 모델을 확인해 주세요."
    );
  case 401:
  case 403:
    return new HttpsError(
      "permission-denied",
      "API 키가 유효하지 않거나 선택한 모델을 사용할 권한이 없습니다."
    );
  case 429:
    return new HttpsError(
      "resource-exhausted",
      "AI Provider의 사용량 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."
    );
  case 408:
  case 503:
  case 529:
    return new HttpsError(
      "unavailable",
      "AI Provider가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요."
    );
  default:
    return new HttpsError(
      "internal",
      "AI 응답을 생성하는 중 오류가 발생했습니다."
    );
  }
}

/**
 * 하이브리드 방식: AI 호출 및 중요 통계(토큰)만 서버에서 처리
 */
export const askGemini = onCall({
  secrets: [geminiKey],
  enforceAppCheck: true,
}, async (request, response) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "로그인이 필요합니다.");
  }

  const prompt = typeof request.data?.prompt === "string" ?
    request.data.prompt.trim() : "";
  const providerValue = request.data?.provider ?? "gemini";
  const modelValue = typeof request.data?.model === "string" ?
    request.data.model.trim() : "";
  const requestedApiKey = typeof request.data?.apiKey === "string" ?
    request.data.apiKey.trim() : "";

  if (!prompt) {
    throw new HttpsError("invalid-argument", "질문이 누락되었습니다.");
  }
  if (prompt.length > 20000) {
    throw new HttpsError("invalid-argument", "질문은 20,000자 이하여야 합니다.");
  }
  if (!isAiProvider(providerValue)) {
    throw new HttpsError("invalid-argument", "지원하지 않는 AI Provider입니다.");
  }
  if (requestedApiKey.length > 512) {
    throw new HttpsError("invalid-argument", "API 키 형식이 올바르지 않습니다.");
  }

  const uid = request.auth.uid;
  const provider = providerValue;
  const apiKey = requestedApiKey ||
    (provider === "gemini" ? geminiKey.value() : "");
  if (!apiKey) {
    throw new HttpsError(
      "failed-precondition",
      `${provider} 개인 API 키를 먼저 설정해 주세요.`
    );
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
      modelValue,
      response?.signal
    );
  } catch (error) {
    logger.error("Stream AI response failed", {error, provider, uid});
    throw toProviderHttpsError(error, provider);
  }

  try {
    const userRef = db.collection("users").doc(uid);
    await userRef.update({
      totalAiTokens: admin.firestore.FieldValue.increment(
        generation.totalTokens
      ),
    });
  } catch {
    logger.error("AI token usage update failed", {provider, uid});
    throw new HttpsError("internal", "AI 사용량을 저장하지 못했습니다.");
  }

  return {
    ...generation,
    provider,
    keySource: requestedApiKey ? "user" : "developer",
    status: "success",
  };
});
