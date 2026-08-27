import {onCall, HttpsError} from "firebase-functions/v2/https";
import {defineSecret} from "firebase-functions/params";
import {setGlobalOptions} from "firebase-functions";
import {GoogleGenerativeAI} from "@google/generative-ai";
import * as logger from "firebase-functions/logger";

// 전역 설정: 최대 인스턴스 제한 (비용 조절)
setGlobalOptions({maxInstances: 10, region: "asia-northeast3"});

// Secret Manager에서 관리할 API 키 정의
const geminiKey = defineSecret("GEMINI_API_KEY");

/**
 * Gemini에게 질문을 보내고 답변을 받는 Callable Function
 */
export const askGemini = onCall({secrets: [geminiKey]}, async (request) => {
  // 1. 인증 확인
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "로그인이 필요한 서비스입니다.");
  }

  const prompt = request.data.prompt;
  if (!prompt || typeof prompt !== "string") {
    throw new HttpsError("invalid-argument", "질문(prompt) 내용을 입력해주세요.");
  }

  try {
    // 2. Gemini SDK 초기화
    const genAI = new GoogleGenerativeAI(geminiKey.value());
    const model = genAI.getGenerativeModel({model: "gemini-3.5-flash-lite"});

    // 3. 답변 생성
    logger.info(`User ${request.auth.uid} asked: ` +
                `${prompt.substring(0, 30)}...`);
    const result = await model.generateContent(prompt);
    const response = await result.response;
    const text = response.text();

    // 4. 결과 반환
    return {
      text: text,
      status: "success",
    };
  } catch (error) {
    logger.error("Gemini API Error:", error);
    throw new HttpsError("internal", "Gemini 답변 생성 중 오류가 발생했습니다.");
  }
});
