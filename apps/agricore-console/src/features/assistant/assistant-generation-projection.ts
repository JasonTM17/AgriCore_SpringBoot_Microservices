import type { AssistantGenerationStatus } from "../../lib/api/types";
import type { DecodedAssistantGenerationEvent } from "./assistant-event-codec";

const MAX_DRAFT_CHARACTERS = 200_000;

export interface AssistantGenerationProjection {
  generationId: string;
  status: AssistantGenerationStatus;
  lastSequence: number;
  draft: string;
  errorCode: string | null;
  assistantMessageId: string | null;
  finishReason: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
}

export type AssistantGenerationEventApplication =
  | { kind: "applied"; projection: AssistantGenerationProjection }
  | { kind: "duplicate"; projection: AssistantGenerationProjection }
  | {
    kind: "gap";
    projection: AssistantGenerationProjection;
    expectedSequence: number;
    actualSequence: number;
  };

export type AssistantGenerationProjectionErrorCode =
  | "EVENT_AFTER_TERMINAL"
  | "ASSISTANT_DRAFT_TOO_LARGE"
  | "PROJECTION_GENERATION_MISMATCH";

export class AssistantGenerationProjectionError extends Error {
  readonly code: AssistantGenerationProjectionErrorCode;

  constructor(code: AssistantGenerationProjectionErrorCode, message: string) {
    super(message);
    this.name = "AssistantGenerationProjectionError";
    this.code = code;
  }
}

export function createAssistantGenerationProjection(
  generationId: string,
): AssistantGenerationProjection {
  return {
    generationId,
    status: "QUEUED",
    lastSequence: -1,
    draft: "",
    errorCode: null,
    assistantMessageId: null,
    finishReason: null,
    inputTokens: null,
    outputTokens: null,
  };
}

export function isTerminalAssistantStatus(status: AssistantGenerationStatus): boolean {
  return status === "COMPLETED" || status === "FAILED" || status === "CANCELLED";
}

function applyContiguousEvent(
  projection: AssistantGenerationProjection,
  decoded: DecodedAssistantGenerationEvent,
): AssistantGenerationProjection {
  if (isTerminalAssistantStatus(projection.status)) {
    throw new AssistantGenerationProjectionError(
      "EVENT_AFTER_TERMINAL",
      "Assistant generation received an event after reaching a terminal state",
    );
  }

  const event = decoded.event;
  const base = { ...projection, lastSequence: event.sequenceNo };
  switch (decoded.eventType) {
    case "STATUS":
      return { ...base, status: decoded.payload.status };
    case "DELTA": {
      const draft = projection.draft + decoded.payload.delta;
      if (draft.length > MAX_DRAFT_CHARACTERS) {
        throw new AssistantGenerationProjectionError(
          "ASSISTANT_DRAFT_TOO_LARGE",
          "Assistant draft exceeded the configured projection limit",
        );
      }
      return { ...base, draft };
    }
    case "COMPLETED":
      return {
        ...base,
        status: "COMPLETED",
        assistantMessageId: decoded.payload.assistantMessageId,
        finishReason: decoded.payload.finishReason,
        inputTokens: decoded.payload.inputTokens ?? null,
        outputTokens: decoded.payload.outputTokens ?? null,
      };
    case "ERROR":
      return { ...base, status: "FAILED", errorCode: decoded.payload.errorCode };
    case "CANCELLED":
      return { ...base, status: "CANCELLED" };
  }
}

export function applyAssistantGenerationEvent(
  projection: AssistantGenerationProjection,
  decoded: DecodedAssistantGenerationEvent,
): AssistantGenerationEventApplication {
  if (decoded.event.generationId !== projection.generationId) {
    throw new AssistantGenerationProjectionError(
      "PROJECTION_GENERATION_MISMATCH",
      "Assistant event did not belong to the projected generation",
    );
  }
  const sequence = decoded.event.sequenceNo;
  if (sequence <= projection.lastSequence) {
    return { kind: "duplicate", projection };
  }

  const expectedSequence = projection.lastSequence + 1;
  if (sequence !== expectedSequence) {
    return {
      kind: "gap",
      projection,
      expectedSequence,
      actualSequence: sequence,
    };
  }

  return {
    kind: "applied",
    projection: applyContiguousEvent(projection, decoded),
  };
}
