import { describe, expect, it } from "vitest";

import type { AssistantGenerationEventType } from "../../lib/api/types";
import type { FetchSseEvent } from "../../lib/streaming/fetch-sse";
import { decodeAssistantSseMessage } from "./assistant-event-codec";

const GENERATION_ID = "20000000-0000-0000-0000-000000000001";

function eventMessage(
  eventType: AssistantGenerationEventType,
  payload: unknown,
  overrides: Partial<{
    id: string;
    event: string;
    generationId: string;
    envelopeType: AssistantGenerationEventType;
    payloadText: string;
  }> = {},
): FetchSseEvent {
  const sequenceNo = 4;
  return {
    id: overrides.id ?? String(sequenceNo),
    event: overrides.event ?? eventType.toLowerCase(),
    data: JSON.stringify({
      id: "10000000-0000-0000-0000-000000000001",
      generationId: overrides.generationId ?? GENERATION_ID,
      sequenceNo,
      eventType: overrides.envelopeType ?? eventType,
      payload: overrides.payloadText ?? JSON.stringify(payload),
      createdAt: "2026-07-20T12:00:00Z",
    }),
  };
}

describe("assistant event codec", () => {
  it.each([
    ["STATUS", { status: "RUNNING" }],
    ["DELTA", { delta: "Cây đang phát triển tốt." }],
    ["COMPLETED", {
      status: "COMPLETED",
      assistantMessageId: "30000000-0000-0000-0000-000000000001",
      finishReason: "stop",
      inputTokens: 12,
      outputTokens: 6,
    }],
    ["ERROR", { status: "FAILED", errorCode: "PROVIDER_UNAVAILABLE" }],
    ["CANCELLED", { status: "CANCELLED" }],
  ] as const)("decodes a valid %s event and its nested payload", (eventType, payload) => {
    const decoded = decodeAssistantSseMessage(eventMessage(eventType, payload), GENERATION_ID);

    expect(decoded).toMatchObject({ kind: "event", event: { eventType }, payload });
  });

  it("decodes a safe stream error without treating its inherited ID as a durable event", () => {
    const decoded = decodeAssistantSseMessage({
      id: "4",
      event: "stream-error",
      data: JSON.stringify({ code: "GENERATION_STREAM_UNAVAILABLE" }),
    }, GENERATION_ID);

    expect(decoded).toEqual({
      kind: "stream-error",
      code: "GENERATION_STREAM_UNAVAILABLE",
    });
  });

  it.each([
    ["MALFORMED_EVENT_DATA", { ...eventMessage("DELTA", { delta: "x" }), data: "{" }],
    ["EVENT_CURSOR_MISMATCH", eventMessage("DELTA", { delta: "x" }, { id: "3" })],
    ["EVENT_TYPE_MISMATCH", eventMessage("DELTA", { delta: "x" }, { event: "status" })],
    ["UNEXPECTED_GENERATION", eventMessage("DELTA", { delta: "x" }, {
      generationId: "20000000-0000-0000-0000-000000000002",
    })],
    ["INVALID_EVENT_PAYLOAD", eventMessage("DELTA", { status: "RUNNING" })],
    ["INVALID_EVENT_PAYLOAD", eventMessage("DELTA", { delta: "x" }, { payloadText: "{" })],
    ["UNSUPPORTED_EVENT_TYPE", eventMessage("DELTA", { delta: "x" }, { event: "mystery" })],
    ["UNSUPPORTED_EVENT_TYPE", eventMessage("DELTA", { delta: "x" }, { event: "DELTA" })],
  ] as const)("rejects protocol violation %s", (code, message) => {
    expect(() => decodeAssistantSseMessage(message, GENERATION_ID)).toThrow(
      expect.objectContaining({ code }),
    );
  });

  it("rejects malformed and unsafe stream-error frames", () => {
    expect(() => decodeAssistantSseMessage({
      id: "",
      event: "stream-error",
      data: JSON.stringify({ code: "unsafe code with details" }),
    }, GENERATION_ID)).toThrow(expect.objectContaining({ code: "INVALID_STREAM_ERROR" }));
  });
});
