import type { AssistantGenerationResponse } from "../../lib/api/types";

const MAX_PROMPT_CHARACTERS = 200_000;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export type AssistantGenerationCommandErrorCode =
  | "PROMPT_REQUIRED"
  | "PROMPT_TOO_LONG"
  | "IDEMPOTENCY_KEY_UNAVAILABLE"
  | "INVALID_GENERATION_RESPONSE";

export class AssistantGenerationCommandError extends Error {
  readonly code: AssistantGenerationCommandErrorCode;

  constructor(code: AssistantGenerationCommandErrorCode, message: string) {
    super(message);
    this.name = "AssistantGenerationCommandError";
    this.code = code;
  }
}

export function normalizeAssistantPrompt(value: string): string {
  const prompt = value.trim();
  if (prompt.length === 0) {
    throw new AssistantGenerationCommandError("PROMPT_REQUIRED", "Assistant prompt is required");
  }
  if (prompt.length > MAX_PROMPT_CHARACTERS) {
    throw new AssistantGenerationCommandError(
      "PROMPT_TOO_LONG",
      "Assistant prompt exceeds the supported limit",
    );
  }
  return prompt;
}

export function createAssistantIdempotencyKey(
  randomUuid: () => string = () => globalThis.crypto.randomUUID(),
): string {
  try {
    const key = randomUuid();
    if (!UUID_PATTERN.test(key)) throw new Error("Invalid UUID");
    return key;
  } catch {
    throw new AssistantGenerationCommandError(
      "IDEMPOTENCY_KEY_UNAVAILABLE",
      "A secure generation key could not be created",
    );
  }
}

export function validateSubmittedGeneration(
  generation: AssistantGenerationResponse,
  expectedConversationId: string,
): AssistantGenerationResponse {
  if (!UUID_PATTERN.test(generation.id)
    || generation.conversationId !== expectedConversationId
    || !UUID_PATTERN.test(generation.conversationId)) {
    throw new AssistantGenerationCommandError(
      "INVALID_GENERATION_RESPONSE",
      "Generation response did not match the submitted conversation",
    );
  }
  return generation;
}
