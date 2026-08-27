import {onCall, HttpsError} from "firebase-functions/v2/https";
import {defineSecret} from "firebase-functions/params";
import {setGlobalOptions} from "firebase-functions";
import {GoogleGenerativeAI} from "@google/generative-ai";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();

setGlobalOptions({maxInstances: 10, region: "asia-northeast3"});

const geminiKey = defineSecret("GEMINI_API_KEY");

/**
 * 하이브리드 방식: AI 호출 및 중요 통계(토큰)만 서버에서 처리
 */
export const askGemini = onCall({secrets: [geminiKey]}, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "로그인이 필요합니다.");
  }

  const {prompt} = request.data;
  if (!prompt) {
    throw new HttpsError("invalid-argument", "질문이 누락되었습니다.");
  }

  const uid = request.auth.uid;
  const modelName = "gemini-2.0-flash-exp";

  try {
    const genAI = new GoogleGenerativeAI(geminiKey.value());
    const model = genAI.getGenerativeModel({model: modelName});

    const result = await model.generateContent(prompt);
    const response = await result.response;
    const text = response.text();
    const usage = response.usageMetadata;
    const totalTokens = (usage?.promptTokenCount || 0) +
                        (usage?.candidatesTokenCount || 0);

    // [보안] 유저의 총 사용 토큰은 서버에서만 업데이트
    const userRef = db.collection("users").doc(uid);
    await userRef.update({
      totalAiTokens: admin.firestore.FieldValue.increment(totalTokens),
    });

    return {
      text: text,
      totalTokens: totalTokens,
      modelName: modelName,
      status: "success",
    };
  } catch (error) {
    logger.error("Gemini Error:", error);
    throw new HttpsError("internal", "AI 응답 생성 중 오류가 발생했습니다.");
  }
});
