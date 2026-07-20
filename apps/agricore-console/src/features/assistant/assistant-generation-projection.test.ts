import { describe, expect, it } from "vitest";

import type { AssistantGenerationEventType } from "../../lib/api/types";
import {
  decodeAssistantGenerationEvent,
  type DecodedAssistantGenerationEvent,
} from "./assistant-event-codec";
import {
  applyAssistantGenerationEvent,
  createAssistantGenerationProjection,
  isTerminalAssistantStatus,
} from "./assistant-generation-projection";

const GENERATION_ID = "20000000-0000-0000-0000-000000000001";

function event(
  sequenceNo: number,
  eventType: AssistantGenerationEventType,
  payload: unknown,
): DecodedAssistantGenerationEvent {
  return decodeAssistantGenerationEvent({
    id: `30000000-0000-0000-0000-${String(sequenceNo).padStart(12, "0")}`,
    generationId: GENERATION_ID,
    sequenceNo,
    eventType,
    payload: JSON.stringify(payload),
    createdAt: "2026-07-20T12:00:00Z",
  }, GENERATION_ID);
}

function apply(
  projection: ReturnType<typeof createAssistantGenerationProjection>,
  decoded: DecodedAssistantGenerationEvent,
) {
  const result = applyAssistantGenerationEvent(projection, decoded);
  expect(result.kind).toBe("applied");
  return result.projection;
}

describe("assistant generation projection", () => {
  it("accumulates contiguous deltas and completion metadata", () => {
    let projection = createAssistantGenerationProjection(GENERATION_ID);
    projection = apply(projection, event(0, "STATUS", { status: "QUEUED" }));
    projection = apply(projection, event(1, "STATUS", { status: "RUNNING" }));
    projection = apply(projection, event(2, "DELTA", { delta: "Xin " }));
    projection = apply(projection, event(3, "DELTA", { delta: "chào" }));
    projection = apply(projection, event(4, "COMPLETED", {
      status: "COMPLETED",
      assistantMessageId: "40000000-0000-0000-0000-000000000001",
      finishReason: "stop",
      inputTokens: 10,
      outputTokens: 4,
    }));

    expect(projection).toMatchObject({
      status: "COMPLETED",
      lastSequence: 4,
      draft: "Xin chào",
      assistantMessageId: "40000000-0000-0000-0000-000000000001",
      finishReason: "stop",
      inputTokens: 10,
      outputTokens: 4,
    });
    expect(isTerminalAssistantStatus(projection.status)).toBe(true);
  });

  it("returns the same projection for already applied replay events", () => {
    const initial = createAssistantGenerationProjection(GENERATION_ID);
    const projection = apply(initial, event(0, "STATUS", { status: "QUEUED" }));

    const result = applyAssistantGenerationEvent(
      projection,
      event(0, "STATUS", { status: "QUEUED" }),
    );

    expect(result).toEqual({ kind: "duplicate", projection });
    expect(result.projection).toBe(projection);
  });

  it("reports a sequence gap without mutating the projection", () => {
    const projection = createAssistantGenerationProjection(GENERATION_ID);

    const result = applyAssistantGenerationEvent(
      projection,
      event(2, "DELTA", { delta: "missing events" }),
    );

    expect(result).toEqual({
      kind: "gap",
      projection,
      expectedSequence: 0,
      actualSequence: 2,
    });
  });

  it("rejects decoded events from another generation", () => {
    const projection = createAssistantGenerationProjection(
      "20000000-0000-0000-0000-000000000002",
    );

    expect(() => applyAssistantGenerationEvent(
      projection,
      event(0, "STATUS", { status: "QUEUED" }),
    )).toThrow(expect.objectContaining({ code: "PROJECTION_GENERATION_MISMATCH" }));
  });

  it("supports direct queued cancellation and terminal provider errors", () => {
    let cancelled = createAssistantGenerationProjection(GENERATION_ID);
    cancelled = apply(cancelled, event(0, "STATUS", { status: "QUEUED" }));
    cancelled = apply(cancelled, event(1, "CANCELLED", { status: "CANCELLED" }));

    let failed = createAssistantGenerationProjection(GENERATION_ID);
    failed = apply(failed, event(0, "STATUS", { status: "QUEUED" }));
    failed = apply(failed, event(1, "STATUS", { status: "RUNNING" }));
    failed = apply(failed, event(2, "ERROR", {
      status: "FAILED",
      errorCode: "PROVIDER_UNAVAILABLE",
    }));

    expect(cancelled.status).toBe("CANCELLED");
    expect(failed).toMatchObject({ status: "FAILED", errorCode: "PROVIDER_UNAVAILABLE" });
  });

  it("rejects new events after terminal state", () => {
    let projection = createAssistantGenerationProjection(GENERATION_ID);
    projection = apply(projection, event(0, "STATUS", { status: "QUEUED" }));
    projection = apply(projection, event(1, "CANCELLED", { status: "CANCELLED" }));

    expect(() => applyAssistantGenerationEvent(
      projection,
      event(2, "DELTA", { delta: "late" }),
    )).toThrow(expect.objectContaining({ code: "EVENT_AFTER_TERMINAL" }));
  });

  it("bounds the accumulated draft to the backend response limit", () => {
    let projection = createAssistantGenerationProjection(GENERATION_ID);
    projection = apply(projection, event(0, "STATUS", { status: "QUEUED" }));
    projection = apply(projection, event(1, "STATUS", { status: "RUNNING" }));
    for (let sequence = 2; sequence < 6; sequence += 1) {
      projection = apply(projection, event(sequence, "DELTA", { delta: "a".repeat(50_000) }));
    }

    expect(projection.draft).toHaveLength(200_000);
    expect(() => applyAssistantGenerationEvent(
      projection,
      event(6, "DELTA", { delta: "overflow" }),
    )).toThrow(expect.objectContaining({ code: "ASSISTANT_DRAFT_TOO_LARGE" }));
  });
});
