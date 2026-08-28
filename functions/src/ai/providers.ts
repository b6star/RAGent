import {GoogleGenAI} from "@google/genai";
import OpenAI from "openai";
import * as logger from "firebase-functions/logger";

export const aiProviders = ["gemini", "openai"] as const;
export type AiProvider = typeof aiProviders[number];

export type AiGenerationResult = {
  text: string;
  inputTokens: number;
  outputTokens: number;
  thoughtsTokens: number;
  totalTokens: number;
  modelName: string;
};

export type AiTextChunkHandler = (text: string) => Promise<void>;

/** Normalized error emitted after a streaming request has started. */
class ProviderStreamError extends Error {
  status?: number;
  code?: string | null;

  /**
   * Creates a normalized stream error.
   * @param {string | null} code Provider error code
   * @param {string} message Provider error message used only for classification
   * @param {number} status Optional HTTP status
   */
  constructor(code: string | null, message: string, status?: number) {
    super(message);
    this.name = "ProviderStreamError";
    this.code = code;
    this.status = status;
  }
}

/**
 * Returns whether a callable request contains a supported provider.
 * @param {unknown} value Provider value from the request
 * @return {boolean} Whether the provider is supported
 */
export function isAiProvider(value: unknown): value is AiProvider {
  return typeof value === "string" &&
    aiProviders.some((provider) => provider === value);
}

/**
 * Streams an SDK response and returns its normalized final result.
 * @param {AiProvider} provider Selected AI provider
 * @param {string} prompt User prompt
 * @param {string} apiKey Provider API key
 * @param {AiTextChunkHandler} onChunk Incremental text handler
 * @param {string} model Preferred model ID
 * @param {AbortSignal} signal Client disconnect signal
 * @return {Promise<AiGenerationResult>} Normalized final response
 */
export async function streamAiResponse(
  provider: AiProvider,
  prompt: string,
  apiKey: string,
  onChunk: AiTextChunkHandler,
  model: string,
  signal?: AbortSignal
): Promise<AiGenerationResult> {
  if (!model) {
    throw new Error("Model ID is required");
  }
  switch (provider) {
  case "gemini":
    return streamGeminiResponse(prompt, apiKey, onChunk, model, signal);
  case "openai":
    return streamOpenAiResponse(prompt, apiKey, onChunk, model, signal);
  }
}

/**
 * Streams a Gemini response through the Google Gen AI SDK.
 * @param {string} prompt User prompt
 * @param {string} apiKey Gemini API key
 * @param {AiTextChunkHandler} onChunk Incremental text handler
 * @param {string} model Preferred model ID
 * @param {AbortSignal} signal Client disconnect signal
 * @return {Promise<AiGenerationResult>} Normalized final response
 */
async function streamGeminiResponse(
  prompt: string,
  apiKey: string,
  onChunk: AiTextChunkHandler,
  model: string,
  signal?: AbortSignal
): Promise<AiGenerationResult> {
  const client = new GoogleGenAI({apiKey});
  const result = await client.models.generateContentStream({
    model: model,
    contents: prompt,
    config: {abortSignal: signal},
  });
  let text = "";
  let lastChunk = null;

  for await (const chunk of result) {
    lastChunk = chunk;
    const delta = chunk.text ?? "";
    if (delta) {
      text += delta;
      await onChunk(delta);
    }
  }

  const completedText = requireText(text, "Gemini");
  const usage = lastChunk?.usageMetadata;
  const inputTokens = usage?.promptTokenCount ?? 0;
  const outputTokens = usage?.candidatesTokenCount ?? 0;
  const totalTokenCount = usage?.totalTokenCount ?? 0;
  const thoughtsTokens = usage?.thoughtsTokenCount ?? 0;
  const totalTokens = totalTokenCount + thoughtsTokens;

  logger.info("Gemini response completed", {
    completedText,
    inputTokens,
    outputTokens,
    totalTokenCount,
    thoughtsTokens,
    totalTokens,
  });

  return {
    text: completedText,
    inputTokens,
    outputTokens,
    thoughtsTokens,
    totalTokens,
    modelName: lastChunk?.modelVersion ?? model,
  };
}

/**
 * Streams an OpenAI response through the Responses API SDK.
 * @param {string} prompt User prompt
 * @param {string} apiKey OpenAI API key
 * @param {AiTextChunkHandler} onChunk Incremental text handler
 * @param {string} model Preferred model ID
 * @param {AbortSignal} signal Client disconnect signal
 * @return {Promise<AiGenerationResult>} Normalized final response
 */
async function streamOpenAiResponse(
  prompt: string,
  apiKey: string,
  onChunk: AiTextChunkHandler,
  model: string,
  signal?: AbortSignal
): Promise<AiGenerationResult> {
  const client = new OpenAI({apiKey});
  const stream = await client.responses.create({
    model: model,
    input: prompt,
    stream: true,
  }, {signal});

  let text = "";
  let inputTokens = 0;
  let outputTokens = 0;
  let thoughtsTokens = 0;
  let totalTokens = 0;

  for await (const event of stream) {
    if (event.type === "error") {
      throw new ProviderStreamError(event.code, event.message);
    }
    if (event.type === "response.failed") {
      const responseError = event.response.error;
      throw new ProviderStreamError(
        responseError?.code ?? null,
        responseError?.message ?? "OpenAI response failed"
      );
    }
    if (event.type === "response.output_text.delta") {
      const delta = event.delta;
      text += delta;
      await onChunk(delta);
    }
    if (event.type === "response.completed") {
      const usage = event.response.usage;
      inputTokens = usage?.input_tokens ?? 0;
      outputTokens = usage?.output_tokens ?? 0;
      thoughtsTokens = usage?.output_tokens_details?.reasoning_tokens ?? 0;
      totalTokens = usage?.total_tokens ?? 0;
    }
  }

  const completedText = requireText(text, "OpenAI");
  return {
    text: completedText,
    inputTokens,
    outputTokens,
    thoughtsTokens,
    totalTokens,
    modelName: model,
  };
}

/**
 * Ensures that a provider produced visible text.
 * @param {string | undefined} value Generated provider text
 * @param {string} providerName Provider name used in the error
 * @return {string} Non-empty trimmed text
 */
function requireText(value: string | undefined, providerName: string): string {
  const text = value?.trim();
  if (!text) {
    throw new ProviderStreamError(
      "empty_response",
      `${providerName} returned an empty response`
    );
  }
  return text;
}
