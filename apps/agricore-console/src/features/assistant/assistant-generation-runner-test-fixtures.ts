import { ApiClient } from "../../lib/api/client";
import type {
  AssistantGenerationEventResponse,
  AssistantGenerationEventType,
} from "../../lib/api/types";
import type { FetchFn } from "../../lib/api/event-stream-test-fixtures";

export const TEST_CONVERSATION_ID = "10000000-0000-0000-0000-000000000001";
export const TEST_GENERATION_ID = "20000000-0000-0000-0000-000000000001";

export function generationEvent(
  sequenceNo: number,
  eventType: AssistantGenerationEventType,
  payload: unknown,
): AssistantGenerationEventResponse {
  return {
    id: `30000000-0000-0000-0000-${String(sequenceNo).padStart(12, "0")}`,
    generationId: TEST_GENERATION_ID,
    sequenceNo,
    eventType,
    payload: JSON.stringify(payload),
    createdAt: "2026-07-20T12:00:00Z",
  };
}

export function eventFrame(value: AssistantGenerationEventResponse): string {
  return `id:${value.sequenceNo}\nevent:${value.eventType.toLowerCase()}\ndata:${JSON.stringify(value)}\n\n`;
}

export function runnerResponse(body: unknown, eventStream = false): Promise<Response> {
  const responseBody = eventStream && typeof body === "string" ? body : JSON.stringify(body);
  return Promise.resolve(new Response(responseBody, {
    headers: { "Content-Type": eventStream ? "text/event-stream" : "application/json" },
  }));
}

export function runnerClient(fetchImpl: FetchFn): ApiClient {
  return new ApiClient({
    getAccessToken: () => "access-token",
    setAccessToken: () => undefined,
    fetchImpl,
  });
}
