import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiClient } from "./client";

type FetchFn = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

function abortAwarePendingFetch(): FetchFn {
  return (_input, init) =>
    new Promise<Response>((_resolve, reject) => {
      const signal = init?.signal;
      if (signal?.aborted) {
        reject(new DOMException("Aborted", "AbortError"));
        return;
      }
      signal?.addEventListener(
        "abort",
        () => reject(new DOMException("Aborted", "AbortError")),
        { once: true },
      );
    });
}

function client(fetchImpl: FetchFn, defaultTimeoutMs: number): ApiClient {
  return new ApiClient({
    getAccessToken: () => "access-token",
    setAccessToken: () => undefined,
    fetchImpl,
    defaultTimeoutMs,
  });
}

afterEach(() => {
  vi.useRealTimers();
});

describe("ApiClient cancellation", () => {
  it("reports an internal deadline as a request timeout", async () => {
    vi.useFakeTimers();
    const request = client(abortAwarePendingFetch(), 25).getCurrentUser();
    const assertion = expect(request).rejects.toMatchObject({
      name: "ApiClientError",
      status: 408,
      code: "REQUEST_TIMEOUT",
      message: "Request timed out",
    });

    await vi.advanceTimersByTimeAsync(25);

    await assertion;
  });

  it("preserves caller cancellation instead of misreporting a timeout", async () => {
    const controller = new AbortController();
    const request = client(abortAwarePendingFetch(), 60_000).getCurrentUser(controller.signal);

    controller.abort();

    await expect(request).rejects.toMatchObject({
      name: "AbortError",
    });
  });
});
