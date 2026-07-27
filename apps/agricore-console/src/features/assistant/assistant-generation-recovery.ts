import { ApiClientError } from "../../lib/api/errors";
import { FetchSseError } from "../../lib/streaming/fetch-sse";
import { AssistantStreamProtocolError } from "./assistant-event-codec";
import { AssistantGenerationProjectionError } from "./assistant-generation-projection";
import { AssistantGenerationReplayError } from "./assistant-generation-replay";
import {
  AssistantStreamLifecycleError,
  AssistantStreamSequenceError,
} from "./assistant-generation-stream";

export const DEFAULT_RECONNECT_DELAYS = [250, 500, 1_000, 2_000, 5_000] as const;

export function validateReconnectDelays(values: readonly number[] | undefined): readonly number[] {
  const delays = values ?? DEFAULT_RECONNECT_DELAYS;
  if (delays.length === 0
    || delays.some((delay) => !Number.isSafeInteger(delay) || delay < 0 || delay > 60_000)) {
    throw new RangeError("reconnectDelaysMs must contain integers between 0 and 60000");
  }
  return delays;
}

function abortError(signal: AbortSignal): Error {
  return signal.reason instanceof Error
    ? signal.reason
    : new DOMException("Aborted", "AbortError");
}

export function waitForReconnect(delayMs: number, signal: AbortSignal): Promise<void> {
  if (signal.aborted) return Promise.reject(abortError(signal));

  return new Promise((resolve, reject) => {
    const timeout = globalThis.setTimeout(() => {
      signal.removeEventListener("abort", onAbort);
      resolve();
    }, delayMs);
    const onAbort = () => {
      globalThis.clearTimeout(timeout);
      reject(abortError(signal));
    };
    signal.addEventListener("abort", onAbort, { once: true });
  });
}

export function recoveryCode(error: unknown): string {
  if (error instanceof ApiClientError
    || error instanceof FetchSseError
    || error instanceof AssistantStreamProtocolError
    || error instanceof AssistantGenerationProjectionError
    || error instanceof AssistantGenerationReplayError
    || error instanceof AssistantStreamLifecycleError
    || error instanceof AssistantStreamSequenceError) {
    return error.code;
  }
  return "ASSISTANT_STREAM_INTERRUPTED";
}

export function fatalRecoveryCode(error: unknown): string | null {
  if (error instanceof FetchSseError
    || error instanceof AssistantStreamProtocolError
    || error instanceof AssistantGenerationProjectionError
    || error instanceof AssistantGenerationReplayError
    || error instanceof AssistantStreamLifecycleError) {
    return error.code;
  }
  if (error instanceof ApiClientError
    && error.status >= 400
    && error.status < 500
    && error.status !== 408
    && error.status !== 429) {
    return error.code;
  }
  return null;
}
