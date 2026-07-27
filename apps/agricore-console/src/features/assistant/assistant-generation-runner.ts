import type { ApiClient } from "../../lib/api/client";
import {
  applyAssistantGenerationEvent,
  type AssistantGenerationProjection,
  createAssistantGenerationProjection,
  isTerminalAssistantStatus,
} from "./assistant-generation-projection";
import {
  fatalRecoveryCode,
  recoveryCode,
  validateReconnectDelays,
  waitForReconnect,
} from "./assistant-generation-recovery";
import { replayAssistantGenerationEvents } from "./assistant-generation-replay";
import {
  AssistantStreamSequenceError,
  streamAssistantGeneration,
} from "./assistant-generation-stream";

export type AssistantGenerationConnectionPhase =
  | "RECONCILING"
  | "CONNECTING"
  | "RECONNECTING"
  | "LIVE"
  | "TERMINAL"
  | "FAILED"
  | "DETACHED";

export interface AssistantGenerationRecoveryNotice {
  attempt: number;
  code: string;
  delayMs: number;
}

export interface AssistantGenerationRunnerOptions {
  conversationId: string;
  generationId: string;
  signal: AbortSignal;
  initialProjection?: AssistantGenerationProjection;
  reconnectDelaysMs?: readonly number[];
  wait?: (delayMs: number, signal: AbortSignal) => Promise<void>;
  onProjection?: (projection: AssistantGenerationProjection) => void | Promise<void>;
  onPhase?: (phase: AssistantGenerationConnectionPhase) => void | Promise<void>;
  onRecovery?: (notice: AssistantGenerationRecoveryNotice) => void | Promise<void>;
}

export interface AssistantGenerationRunnerResult {
  kind: "terminal" | "detached" | "failed";
  projection: AssistantGenerationProjection;
  errorCode?: string;
}

export async function runAssistantGeneration(
  api: ApiClient,
  options: AssistantGenerationRunnerOptions,
): Promise<AssistantGenerationRunnerResult> {
  const delays = validateReconnectDelays(options.reconnectDelaysMs);
  const wait = options.wait ?? waitForReconnect;
  let projection = options.initialProjection
    ?? createAssistantGenerationProjection(options.generationId);
  if (projection.generationId !== options.generationId) {
    throw new RangeError("initialProjection must belong to generationId");
  }
  let failureStreak = 0;

  while (true) {
    if (options.signal.aborted) {
      await options.onPhase?.("DETACHED");
      return { kind: "detached", projection };
    }
    if (isTerminalAssistantStatus(projection.status)) {
      await options.onPhase?.("TERMINAL");
      return { kind: "terminal", projection };
    }

    const cycleStartSequence = projection.lastSequence;
    let failureCode = "ASSISTANT_STREAM_ENDED";
    try {
      await options.onPhase?.("RECONCILING");
      projection = await replayAssistantGenerationEvents(
        api,
        options.conversationId,
        projection,
        {
          signal: options.signal,
          ...(options.onProjection ? { onProjection: options.onProjection } : {}),
        },
      );
      if (isTerminalAssistantStatus(projection.status)) continue;

      await options.onPhase?.(failureStreak === 0 ? "CONNECTING" : "RECONNECTING");
      const streamResult = await streamAssistantGeneration(api, {
        conversationId: options.conversationId,
        generationId: options.generationId,
        afterSequence: projection.lastSequence,
        signal: options.signal,
        onHeartbeat: () => options.onPhase?.("LIVE"),
        onEvent: async (decoded) => {
          const application = applyAssistantGenerationEvent(projection, decoded);
          if (application.kind !== "applied") {
            throw new AssistantStreamSequenceError(
              projection.lastSequence + 1,
              decoded.event.sequenceNo,
            );
          }
          projection = application.projection;
          await options.onPhase?.("LIVE");
          await options.onProjection?.(projection);
        },
      });
      failureCode = streamResult.streamErrorCode ?? failureCode;
      if (isTerminalAssistantStatus(projection.status)) continue;
    } catch (error) {
      if (options.signal.aborted) {
        await options.onPhase?.("DETACHED");
        return { kind: "detached", projection };
      }
      const fatalCode = fatalRecoveryCode(error);
      if (fatalCode) {
        await options.onPhase?.("FAILED");
        return { kind: "failed", projection, errorCode: fatalCode };
      }
      failureCode = recoveryCode(error);
    }

    failureStreak = projection.lastSequence > cycleStartSequence ? 1 : failureStreak + 1;
    const delayMs = delays[Math.min(failureStreak - 1, delays.length - 1)] ?? 0;
    await options.onRecovery?.({ attempt: failureStreak, code: failureCode, delayMs });
    try {
      await wait(delayMs, options.signal);
    } catch (error) {
      if (!options.signal.aborted) throw error;
      await options.onPhase?.("DETACHED");
      return { kind: "detached", projection };
    }
  }
}
