import { z } from "zod";

import type { AssistantGenerationEventResponse } from "../../lib/api/types";
import type { FetchSseEvent } from "../../lib/streaming/fetch-sse";

const eventTypeSchema = z.enum(["STATUS", "DELTA", "COMPLETED", "ERROR", "CANCELLED"]);
const eventTypesByName: Readonly<Record<string, z.infer<typeof eventTypeSchema>>> = {
  status: "STATUS",
  delta: "DELTA",
  completed: "COMPLETED",
  error: "ERROR",
  cancelled: "CANCELLED",
};
const safeIntegerSchema = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const safeCodeSchema = z.string().min(1).max(128).regex(/^[A-Z0-9_]+$/);
const uuidSchema = z.string().regex(
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
);

const eventEnvelopeSchema = z.object({
  id: uuidSchema,
  generationId: uuidSchema,
  sequenceNo: safeIntegerSchema,
  eventType: eventTypeSchema,
  payload: z.string().max(65_536),
  createdAt: z.iso.datetime({ offset: true }),
}).strict();

const statusPayloadSchema = z.object({
  status: z.enum(["QUEUED", "RUNNING", "CANCEL_REQUESTED"]),
}).strict();
const deltaPayloadSchema = z.object({ delta: z.string().min(1) }).strict();
const completedPayloadSchema = z.object({
  status: z.literal("COMPLETED"),
  assistantMessageId: uuidSchema,
  finishReason: z.string().min(1).max(128),
  inputTokens: safeIntegerSchema.optional(),
  outputTokens: safeIntegerSchema.optional(),
}).strict();
const errorPayloadSchema = z.object({
  status: z.literal("FAILED"),
  errorCode: safeCodeSchema,
}).strict();
const cancelledPayloadSchema = z.object({ status: z.literal("CANCELLED") }).strict();
const streamErrorSchema = z.object({ code: safeCodeSchema }).strict();

export type AssistantStatusEventPayload = z.infer<typeof statusPayloadSchema>;
export type AssistantDeltaEventPayload = z.infer<typeof deltaPayloadSchema>;
export type AssistantCompletedEventPayload = z.infer<typeof completedPayloadSchema>;
export type AssistantErrorEventPayload = z.infer<typeof errorPayloadSchema>;
export type AssistantCancelledEventPayload = z.infer<typeof cancelledPayloadSchema>;

type TypedEnvelope<T extends AssistantGenerationEventResponse["eventType"]> =
  AssistantGenerationEventResponse & { eventType: T };

export type DecodedAssistantGenerationEvent =
  | { kind: "event"; event: TypedEnvelope<"STATUS">; payload: AssistantStatusEventPayload }
  | { kind: "event"; event: TypedEnvelope<"DELTA">; payload: AssistantDeltaEventPayload }
  | { kind: "event"; event: TypedEnvelope<"COMPLETED">; payload: AssistantCompletedEventPayload }
  | { kind: "event"; event: TypedEnvelope<"ERROR">; payload: AssistantErrorEventPayload }
  | { kind: "event"; event: TypedEnvelope<"CANCELLED">; payload: AssistantCancelledEventPayload };

export interface DecodedAssistantStreamError {
  kind: "stream-error";
  code: string;
}

export type DecodedAssistantSseMessage =
  | DecodedAssistantGenerationEvent
  | DecodedAssistantStreamError;

export type AssistantStreamProtocolErrorCode =
  | "MALFORMED_EVENT_DATA"
  | "INVALID_EVENT_ENVELOPE"
  | "UNSUPPORTED_EVENT_TYPE"
  | "EVENT_CURSOR_MISMATCH"
  | "EVENT_TYPE_MISMATCH"
  | "UNEXPECTED_GENERATION"
  | "INVALID_EVENT_PAYLOAD"
  | "INVALID_STREAM_ERROR";

export class AssistantStreamProtocolError extends Error {
  readonly code: AssistantStreamProtocolErrorCode;

  constructor(code: AssistantStreamProtocolErrorCode, message: string) {
    super(message);
    this.name = "AssistantStreamProtocolError";
    this.code = code;
  }
}

function parseJson(value: string, code: AssistantStreamProtocolErrorCode): unknown {
  try {
    return JSON.parse(value) as unknown;
  } catch {
    throw new AssistantStreamProtocolError(code, "Assistant stream contained malformed JSON");
  }
}

function parseSchema<T>(
  schema: z.ZodType<T>,
  value: unknown,
  code: AssistantStreamProtocolErrorCode,
): T {
  const result = schema.safeParse(value);
  if (!result.success) {
    throw new AssistantStreamProtocolError(code, "Assistant stream violated its contract");
  }
  return result.data;
}

function decodePayload(
  event: AssistantGenerationEventResponse,
): DecodedAssistantGenerationEvent {
  const value = parseJson(event.payload, "INVALID_EVENT_PAYLOAD");
  switch (event.eventType) {
    case "STATUS":
      return { kind: "event", event: { ...event, eventType: "STATUS" },
        payload: parseSchema(statusPayloadSchema, value, "INVALID_EVENT_PAYLOAD") };
    case "DELTA":
      return { kind: "event", event: { ...event, eventType: "DELTA" },
        payload: parseSchema(deltaPayloadSchema, value, "INVALID_EVENT_PAYLOAD") };
    case "COMPLETED":
      return { kind: "event", event: { ...event, eventType: "COMPLETED" },
        payload: parseSchema(completedPayloadSchema, value, "INVALID_EVENT_PAYLOAD") };
    case "ERROR":
      return { kind: "event", event: { ...event, eventType: "ERROR" },
        payload: parseSchema(errorPayloadSchema, value, "INVALID_EVENT_PAYLOAD") };
    case "CANCELLED":
      return { kind: "event", event: { ...event, eventType: "CANCELLED" },
        payload: parseSchema(cancelledPayloadSchema, value, "INVALID_EVENT_PAYLOAD") };
  }
}

export function decodeAssistantSseMessage(
  message: FetchSseEvent,
  expectedGenerationId: string,
): DecodedAssistantSseMessage {
  if (message.event === "stream-error") {
    const value = parseJson(message.data, "INVALID_STREAM_ERROR");
    const error = parseSchema(streamErrorSchema, value, "INVALID_STREAM_ERROR");
    return { kind: "stream-error", code: error.code };
  }

  const expectedType = eventTypesByName[message.event];
  if (!expectedType) {
    throw new AssistantStreamProtocolError(
      "UNSUPPORTED_EVENT_TYPE",
      "Assistant stream used an unsupported event type",
    );
  }
  const rawEnvelope = parseJson(message.data, "MALFORMED_EVENT_DATA");
  const event = parseSchema(eventEnvelopeSchema, rawEnvelope, "INVALID_EVENT_ENVELOPE");
  if (message.id !== String(event.sequenceNo)) {
    throw new AssistantStreamProtocolError("EVENT_CURSOR_MISMATCH", "Event cursor did not match sequence");
  }
  if (event.eventType !== expectedType) {
    throw new AssistantStreamProtocolError("EVENT_TYPE_MISMATCH", "Event name did not match payload type");
  }
  if (event.generationId !== expectedGenerationId) {
    throw new AssistantStreamProtocolError("UNEXPECTED_GENERATION", "Event belonged to another generation");
  }
  return decodePayload(event);
}
