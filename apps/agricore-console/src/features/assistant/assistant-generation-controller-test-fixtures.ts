import { ApiClient } from "../../lib/api/client";
import type { AssistantGenerationResponse } from "../../lib/api/types";
import type { FetchFn } from "../../lib/api/event-stream-test-fixtures";

export const TEST_CONVERSATION_ID = "10000000-0000-0000-0000-000000000001";
export const TEST_GENERATION_ID = "20000000-0000-0000-0000-000000000001";
export const TEST_IDEMPOTENCY_KEY = "30000000-0000-0000-0000-000000000001";

export function generationResponse(
  overrides: Partial<AssistantGenerationResponse> = {},
): AssistantGenerationResponse {
  return {
    id: TEST_GENERATION_ID,
    conversationId: TEST_CONVERSATION_ID,
    status: "QUEUED",
    provider: "openai",
    model: null,
    errorCode: null,
    userMessageId: "40000000-0000-0000-0000-000000000001",
    nextEventSequence: 1,
    queuedAt: "2026-07-21T00:00:00Z",
    createdAt: "2026-07-21T00:00:00Z",
    updatedAt: "2026-07-21T00:00:00Z",
    completedAt: null,
    deduplicated: false,
    ...overrides,
  };
}

export function jsonResponse(body: unknown): Promise<Response> {
  return Promise.resolve(new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
  }));
}

export function controllerApi(fetchImpl: FetchFn): ApiClient {
  return new ApiClient({
    getAccessToken: () => "access-token",
    setAccessToken: () => undefined,
    fetchImpl,
  });
}
