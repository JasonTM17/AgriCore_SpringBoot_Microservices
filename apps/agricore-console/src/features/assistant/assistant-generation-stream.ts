import type { ApiClient, EventStreamRequestOptions } from "../../lib/api/client";
import { readFetchSse } from "../../lib/streaming/fetch-sse";
import type { DecodedAssistantGenerationEvent } from "./assistant-event-codec";
import { decodeAssistantSseMessage } from "./assistant-event-codec";

export interface AssistantGenerationStreamOptions {
  conversationId: string;
  generationId: string;
  afterSequence: number;
  signal?: AbortSignal;
  onEvent: (event: DecodedAssistantGenerationEvent) => void | Promise<void>;
  onHeartbeat?: () => void | Promise<void>;
  onStreamError?: (code: string) => void | Promise<void>;
}

export interface AssistantGenerationStreamResult {
  lastSequence: number;
  streamErrorCode: string | null;
}

export class AssistantStreamSequenceError extends Error {
  readonly code = "NONCONTIGUOUS_EVENT_STREAM";
  readonly expectedSequence: number;
  readonly actualSequence: number;

  constructor(expectedSequence: number, actualSequence: number) {
    super("Assistant event stream was not contiguous");
    this.name = "AssistantStreamSequenceError";
    this.expectedSequence = expectedSequence;
    this.actualSequence = actualSequence;
  }
}

export class AssistantStreamLifecycleError extends Error {
  readonly code = "EVENT_AFTER_STREAM_ERROR";

  constructor() {
    super("Assistant stream continued after a terminal stream error");
    this.name = "AssistantStreamLifecycleError";
  }
}

function requireCursor(value: number): void {
  if (!Number.isSafeInteger(value) || value < -1) {
    throw new RangeError("afterSequence must be -1 or a non-negative safe integer");
  }
}

function streamPath(conversationId: string, generationId: string, afterSequence: number): string {
  const conversation = encodeURIComponent(conversationId);
  const generation = encodeURIComponent(generationId);
  return `/api/v1/assistant/conversations/${conversation}/generations/${generation}/events?after=${afterSequence}`;
}

export async function streamAssistantGeneration(
  api: ApiClient,
  options: AssistantGenerationStreamOptions,
): Promise<AssistantGenerationStreamResult> {
  requireCursor(options.afterSequence);
  let lastSequence = options.afterSequence;
  let streamErrorCode: string | null = null;
  const headers = options.afterSequence >= 0
    ? { "Last-Event-ID": String(options.afterSequence) }
    : undefined;
  const requestOptions: EventStreamRequestOptions = {
    ...(headers ? { headers } : {}),
    ...(options.signal ? { signal: options.signal } : {}),
  };

  return api.withEventStream(
    streamPath(options.conversationId, options.generationId, options.afterSequence),
    requestOptions,
    async (response) => {
      await readFetchSse(response.body, {
        ...(options.signal ? { signal: options.signal } : {}),
        idleTimeoutMs: 45_000,
        onComment: async (comment) => {
          if (streamErrorCode) throw new AssistantStreamLifecycleError();
          if (comment === "heartbeat") await options.onHeartbeat?.();
        },
        onEvent: async (rawEvent) => {
          if (streamErrorCode) throw new AssistantStreamLifecycleError();
          const decoded = decodeAssistantSseMessage(rawEvent, options.generationId);
          if (decoded.kind === "stream-error") {
            streamErrorCode = decoded.code;
            await options.onStreamError?.(decoded.code);
            return;
          }

          const expectedSequence = lastSequence + 1;
          if (decoded.event.sequenceNo !== expectedSequence) {
            throw new AssistantStreamSequenceError(
              expectedSequence,
              decoded.event.sequenceNo,
            );
          }
          await options.onEvent(decoded);
          lastSequence = decoded.event.sequenceNo;
        },
      });
      return { lastSequence, streamErrorCode };
    },
  );
}
