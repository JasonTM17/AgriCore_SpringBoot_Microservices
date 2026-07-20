import type { ApiClient } from "../../lib/api/client";
import { listAssistantGenerationEvents } from "./assistant-api";
import { decodeAssistantGenerationEvent } from "./assistant-event-codec";
import {
  applyAssistantGenerationEvent,
  type AssistantGenerationProjection,
  isTerminalAssistantStatus,
} from "./assistant-generation-projection";

const DEFAULT_BATCH_SIZE = 1_000;
const DEFAULT_MAX_BATCHES = 256;

export interface AssistantGenerationReplayOptions {
  signal?: AbortSignal;
  batchSize?: number;
  maxBatches?: number;
  onProjection?: (projection: AssistantGenerationProjection) => void | Promise<void>;
}

export type AssistantGenerationReplayErrorCode =
  | "NONCONTIGUOUS_EVENT_REPLAY"
  | "EVENT_REPLAY_DID_NOT_ADVANCE"
  | "EVENT_REPLAY_BATCH_LIMIT_EXCEEDED"
  | "INVALID_EVENT_REPLAY_BATCH";

export class AssistantGenerationReplayError extends Error {
  readonly code: AssistantGenerationReplayErrorCode;
  readonly expectedSequence: number | undefined;
  readonly actualSequence: number | undefined;

  constructor(
    code: AssistantGenerationReplayErrorCode,
    message: string,
    expectedSequence?: number,
    actualSequence?: number,
  ) {
    super(message);
    this.name = "AssistantGenerationReplayError";
    this.code = code;
    this.expectedSequence = expectedSequence;
    this.actualSequence = actualSequence;
  }
}

function positiveInteger(value: number | undefined, fallback: number, maximum: number): number {
  const resolved = value ?? fallback;
  if (!Number.isSafeInteger(resolved) || resolved < 1 || resolved > maximum) {
    throw new RangeError(`value must be an integer between 1 and ${maximum}`);
  }
  return resolved;
}

export async function replayAssistantGenerationEvents(
  api: ApiClient,
  conversationId: string,
  initial: AssistantGenerationProjection,
  options: AssistantGenerationReplayOptions = {},
): Promise<AssistantGenerationProjection> {
  if (isTerminalAssistantStatus(initial.status)) return initial;

  const batchSize = positiveInteger(options.batchSize, DEFAULT_BATCH_SIZE, 1_000);
  const maxBatches = positiveInteger(
    options.maxBatches,
    DEFAULT_MAX_BATCHES,
    Number.MAX_SAFE_INTEGER,
  );
  let projection = initial;

  for (let batchIndex = 0; batchIndex < maxBatches; batchIndex += 1) {
    const previousSequence = projection.lastSequence;
    const events = await listAssistantGenerationEvents(
      api,
      conversationId,
      projection.generationId,
      { after: projection.lastSequence, limit: batchSize },
      options.signal,
    );
    if (!Array.isArray(events) || events.length > batchSize) {
      throw new AssistantGenerationReplayError(
        "INVALID_EVENT_REPLAY_BATCH",
        "Assistant event replay returned an invalid batch",
      );
    }

    for (const rawEvent of events) {
      const decoded = decodeAssistantGenerationEvent(rawEvent, projection.generationId);
      const application = applyAssistantGenerationEvent(projection, decoded);
      if (application.kind === "gap") {
        throw new AssistantGenerationReplayError(
          "NONCONTIGUOUS_EVENT_REPLAY",
          "Assistant event replay contained a sequence gap",
          application.expectedSequence,
          application.actualSequence,
        );
      }
      projection = application.projection;
    }

    if (projection.lastSequence !== previousSequence) {
      await options.onProjection?.(projection);
    }
    if (isTerminalAssistantStatus(projection.status) || events.length < batchSize) {
      return projection;
    }
    if (projection.lastSequence === previousSequence) {
      throw new AssistantGenerationReplayError(
        "EVENT_REPLAY_DID_NOT_ADVANCE",
        "Assistant event replay returned a full batch without cursor progress",
      );
    }
  }

  throw new AssistantGenerationReplayError(
    "EVENT_REPLAY_BATCH_LIMIT_EXCEEDED",
    "Assistant event replay exceeded its bounded batch count",
  );
}
