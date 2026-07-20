import type { AccessTokenProvider } from "./client-options";
import { ApiClientError, parseApiError } from "./errors";
import {
  createRequestCancellation,
  type RequestCancellation,
} from "./request-cancellation";

export interface EventStreamRequestOptions {
  signal?: AbortSignal;
  timeoutMs?: number;
  headers?: Record<string, string>;
}

export type EventStreamResponse = Response & { readonly body: ReadableStream<Uint8Array> };

export type EventStreamConsumer<T> = (
  response: EventStreamResponse,
  signal: AbortSignal,
) => Promise<T>;

export interface EventStreamRequestDependencies {
  baseUrl: string;
  getAccessToken: AccessTokenProvider;
  fetchImpl: typeof fetch;
  defaultTimeoutMs: number;
  refreshAccessToken: () => Promise<boolean>;
  clearSession: () => void;
}

interface OpenedEventStream {
  response: Response;
  cancellation: RequestCancellation;
}

function timeoutError(): ApiClientError {
  return new ApiClientError(408, null, "Event stream connection timed out", {
    fallbackCode: "EVENT_STREAM_TIMEOUT",
  });
}

async function openEventStream(
  path: string,
  options: EventStreamRequestOptions,
  dependencies: EventStreamRequestDependencies,
): Promise<OpenedEventStream> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "text/event-stream");
  const token = dependencies.getAccessToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const cancellation = createRequestCancellation(
    options.signal,
    options.timeoutMs ?? dependencies.defaultTimeoutMs,
  );
  try {
    const response = await dependencies.fetchImpl(`${dependencies.baseUrl}${path}`, {
      method: "GET",
      headers,
      credentials: "include",
      cache: "no-store",
      signal: cancellation.signal,
    });
    if (cancellation.didTimeout()) {
      cancellation.dispose();
      throw timeoutError();
    }
    cancellation.releaseTimeout();
    return { response, cancellation };
  } catch (error) {
    cancellation.dispose();
    if (cancellation.didTimeout()) {
      throw timeoutError();
    }
    throw error;
  }
}

function isEventStream(response: Response): boolean {
  return response.headers.get("Content-Type")
    ?.split(";", 1)[0]
    ?.trim()
    .toLowerCase() === "text/event-stream";
}

async function consumeOpenedStream<T>(
  opened: OpenedEventStream,
  consumer: EventStreamConsumer<T>,
  clearSession: () => void,
): Promise<T> {
  const { response, cancellation } = opened;
  try {
    if (!response.ok) {
      if (response.status === 401) {
        clearSession();
      }
      throw await parseApiError(response);
    }
    if (!isEventStream(response) || !response.body) {
      await response.body?.cancel().catch(() => undefined);
      throw new ApiClientError(response.status, null, "Invalid event stream response", {
        fallbackCode: "INVALID_EVENT_STREAM_RESPONSE",
      });
    }
    return await consumer(response as EventStreamResponse, cancellation.signal);
  } finally {
    cancellation.dispose();
  }
}

export async function requestAuthenticatedEventStream<T>(
  path: string,
  options: EventStreamRequestOptions,
  consumer: EventStreamConsumer<T>,
  dependencies: EventStreamRequestDependencies,
): Promise<T> {
  const first = await openEventStream(path, options, dependencies);
  if (first.response.status !== 401) {
    return consumeOpenedStream(first, consumer, dependencies.clearSession);
  }

  first.cancellation.dispose();
  const unauthorized = await parseApiError(first.response);
  if (!await dependencies.refreshAccessToken()) {
    dependencies.clearSession();
    throw unauthorized;
  }

  const retry = await openEventStream(path, options, dependencies);
  return consumeOpenedStream(retry, consumer, dependencies.clearSession);
}
