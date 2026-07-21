import { describe, expect, it } from "vitest";

import type { AssistantGenerationResponse } from "../../lib/api/types";
import {
  createAssistantIdempotencyKey,
  normalizeAssistantPrompt,
  validateSubmittedGeneration,
} from "./assistant-generation-command";

const CONVERSATION_ID = "10000000-0000-0000-0000-000000000001";

function generation(overrides: Partial<AssistantGenerationResponse> = {}): AssistantGenerationResponse {
  return {
    id: "20000000-0000-0000-0000-000000000001",
    conversationId: CONVERSATION_ID,
    status: "QUEUED",
    provider: "openai",
    model: null,
    errorCode: null,
    userMessageId: "30000000-0000-0000-0000-000000000001",
    nextEventSequence: 1,
    queuedAt: "2026-07-20T12:00:00Z",
    createdAt: "2026-07-20T12:00:00Z",
    updatedAt: "2026-07-20T12:00:00Z",
    completedAt: null,
    deduplicated: false,
    ...overrides,
  };
}

describe("assistant generation command", () => {
  it("trims only prompt boundaries and preserves internal formatting", () => {
    expect(normalizeAssistantPrompt("  Dòng một\n\nDòng hai  ")).toBe("Dòng một\n\nDòng hai");
  });

  it.each([
    ["PROMPT_REQUIRED", "   "],
    ["PROMPT_TOO_LONG", "a".repeat(200_001)],
  ])("rejects invalid prompt with %s", (code, prompt) => {
    expect(() => normalizeAssistantPrompt(prompt)).toThrow(expect.objectContaining({ code }));
  });

  it("uses an injectable secure UUID source and rejects malformed keys", () => {
    const key = createAssistantIdempotencyKey(
      () => "40000000-0000-0000-0000-000000000001",
    );

    expect(key).toBe("40000000-0000-0000-0000-000000000001");
    expect(() => createAssistantIdempotencyKey(() => "predictable-key"))
      .toThrow(expect.objectContaining({ code: "IDEMPOTENCY_KEY_UNAVAILABLE" }));
  });

  it("correlates submitted generation authority to the requested conversation", () => {
    const valid = generation();

    expect(validateSubmittedGeneration(valid, CONVERSATION_ID)).toBe(valid);
    expect(() => validateSubmittedGeneration(
      generation({ conversationId: "10000000-0000-0000-0000-000000000002" }),
      CONVERSATION_ID,
    )).toThrow(expect.objectContaining({ code: "INVALID_GENERATION_RESPONSE" }));
    expect(() => validateSubmittedGeneration(
      generation({ id: "not-a-uuid" }),
      CONVERSATION_ID,
    )).toThrow(expect.objectContaining({ code: "INVALID_GENERATION_RESPONSE" }));
    expect(() => validateSubmittedGeneration(
      generation(),
      CONVERSATION_ID,
      "20000000-0000-0000-0000-000000000002",
    )).toThrow(expect.objectContaining({ code: "INVALID_GENERATION_RESPONSE" }));
  });
});
