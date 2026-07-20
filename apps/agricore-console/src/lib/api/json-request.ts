import type { AccessTokenProvider, RequestOptions } from "./client-options";
import { ApiClientError } from "./errors";
import { createRequestCancellation } from "./request-cancellation";

export interface JsonRequestDependencies {
  baseUrl: string;
  getAccessToken: AccessTokenProvider;
  fetchImpl: typeof fetch;
  defaultTimeoutMs: number;
}

export async function requestJsonResponse(
  path: string,
  options: RequestOptions,
  dependencies: JsonRequestDependencies,
): Promise<Response> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");

  if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  if (options.auth !== false) {
    const token = dependencies.getAccessToken();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }
  }

  const cancellation = createRequestCancellation(
    options.signal,
    options.timeoutMs ?? dependencies.defaultTimeoutMs,
  );
  const init: RequestInit = {
    method: options.method ?? "GET",
    headers,
    credentials: options.credentials ?? "include",
    ...(options.cache ? { cache: options.cache } : {}),
    signal: cancellation.signal,
  };
  if (options.body !== undefined) {
    init.body = JSON.stringify(options.body);
  }

  try {
    return await dependencies.fetchImpl(`${dependencies.baseUrl}${path}`, init);
  } catch (error) {
    if (cancellation.didTimeout()) {
      throw new ApiClientError(408, null, "Request timed out", {
        fallbackCode: "REQUEST_TIMEOUT",
      });
    }
    throw error;
  } finally {
    cancellation.dispose();
  }
}
