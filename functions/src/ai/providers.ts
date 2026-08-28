import Anthropic from "@anthropic-ai/sdk";
import {GoogleGenAI} from "@google/genai";
import OpenAI from "openai";

export const aiProviders = ["gemini", "openai", "anthropic"] as const;
export type AiProvider = typeof aiProviders[number];

export type AiGenerationResult = {
  text: string;
  totalTokens: number;
  modelName: string;
};

export type AiTextChunkHandler = (text: string) => Promise<void>;

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
  case "anthropic":
    return streamAnthropicResponse(prompt, apiKey, onChunk, model, signal);
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

  return {
    text: completedText,
    totalTokens: usage?.totalTokenCount ?? 0,
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
  const stream = await client.chat.completions.create({
    model: model,
    messages: [{role: "user", content: prompt}],
    stream: true,
  }, {signal});

  let text = "";
  let usage = null;

  for await (const chunk of stream) {
    const delta = chunk.choices[0]?.delta?.content || "";
    if (delta) {
      text += delta;
      await onChunk(delta);
    }
    if (chunk.usage) {
      usage = chunk.usage;
    }
  }

  const completedText = requireText(text, "OpenAI");
  return {
    text: completedText,
    totalTokens: usage?.total_tokens ?? 0,
    modelName: model,
  };
}

/**
 * Streams an Anthropic response through the Messages API SDK.
 * @param {string} prompt User prompt
 * @param {string} apiKey Anthropic API key
 * @param {AiTextChunkHandler} onChunk Incremental text handler
 * @param {string} model Preferred model ID
 * @param {AbortSignal} signal Client disconnect signal
 * @return {Promise<AiGenerationResult>} Normalized final response
 */
async function streamAnthropicResponse(
  prompt: string,
  apiKey: string,
  onChunk: AiTextChunkHandler,
  model: string,
  signal?: AbortSignal
): Promise<AiGenerationResult> {
  const client = new Anthropic({apiKey});
  const stream = await client.messages.create({
    model: model,
    max_tokens: 4096,
    messages: [{role: "user", content: prompt}],
    stream: true,
  }, {signal});

  let text = "";
  for await (const event of stream) {
    if (
      event.type === "content_block_delta" &&
      event.delta.type === "text_delta"
    ) {
      text += event.delta.text;
      await onChunk(event.delta.text);
    }
  }

  const completedText = requireText(text, "Anthropic");
  return {
    text: completedText,
    totalTokens: 0,
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
  if (!text) throw new Error(`${providerName} returned an empty response`);
  return text;
}
