import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiClient } from "./client";
import {
  type FetchFn,
  jsonResponse,
  refreshResponse,
  requestUrl,
  streamResponse,
} from "./event-stream-test-fixtures";

afterEach(() => {
  vi.useRealTimers();
});

describe("authenticated event-stream request", () => {
  it("sends bearer auth and resume cursor without exposing the token in the URL", async () => {
    const fetchImpl = vi.fn<FetchFn>(() => Promise.resolve(streamResponse()));
    const client = new ApiClient({
      baseUrl: "https://gateway.agricore.test",
      getAccessToken: () => "private-access-token",
      setAccessToken: () => undefined,
      fetchImpl,
    });

    const text = await client.withEventStream(
      "/api/v1/assistant/events?after=3",
      { headers: { "Last-Event-ID": "3" } },
      (response) => response.text(),
    );

    expect(text).toContain("ready");
    const [input, init] = vi.mocked(fetchImpl).mock.calls[0] ?? [];
    expect(input).toBe("https://gateway.agricore.test/api/v1/assistant/events?after=3");
    expect(input ? requestUrl(input) : "").not.toContain("private-access-token");
    expect(init?.method).toBe("GET");
    expect(init?.credentials).toBe("include");
    expect(init?.cache).toBe("no-store");
    const headers = new Headers(init?.headers);
    expect(headers.get("Accept")).toBe("text/event-stream");
    expect(headers.get("Authorization")).toBe("Bearer private-access-token");
    expect(headers.get("Last-Event-ID")).toBe("3");
  });

  it("single-flights concurrent 401 refreshes and retries with the fresh token", async () => {
    let token = "stale-token";
    let refreshCalls = 0;
    const fetchImpl = vi.fn<FetchFn>(async (input, init) => {
      if (requestUrl(input).endsWith("/api/v1/auth/web/refresh")) {
        refreshCalls += 1;
        await new Promise((resolve) => setTimeout(resolve, 10));
        token = "fresh-token";
        return refreshResponse();
      }
      const auth = new Headers(init?.headers).get("Authorization");
      return auth === "Bearer fresh-token"
        ? streamResponse()
        : jsonResponse(401, { code: "UNAUTHORIZED", message: "expired" });
    });
    const client = new ApiClient({
      getAccessToken: () => token,
      setAccessToken: (value) => {
        token = value ?? "";
      },
      fetchImpl,
    });

    const consume = () => client.withEventStream("/events", {}, (response) => response.text());
    const [first, second] = await Promise.all([consume(), consume()]);

    expect(first).toContain("ready");
    expect(second).toContain("ready");
    expect(refreshCalls).toBe(1);
    expect(token).toBe("fresh-token");
  });

  it("keeps caller cancellation connected after response headers arrive", async () => {
    vi.useFakeTimers();
    const caller = new AbortController();
    let fetchSignal: AbortSignal | undefined;
    let markConsumerStarted: (() => void) | undefined;
    const consumerStarted = new Promise<void>((resolve) => {
      markConsumerStarted = resolve;
    });
    const fetchImpl = vi.fn<FetchFn>((_input, init) => {
      fetchSignal = init?.signal ?? undefined;
      const body = new ReadableStream<Uint8Array>({
        start(controller) {
          fetchSignal?.addEventListener("abort", () => {
            controller.error(new DOMException("Aborted", "AbortError"));
          }, { once: true });
        },
      });
      return Promise.resolve(new Response(body, {
        headers: { "Content-Type": "text/event-stream" },
      }));
    });
    const client = new ApiClient({
      getAccessToken: () => "access-token",
      setAccessToken: () => undefined,
      fetchImpl,
      defaultTimeoutMs: 25,
    });

    const stream = client.withEventStream("/events", { signal: caller.signal }, async (response) => {
      markConsumerStarted?.();
      await response.body?.getReader().read();
    });

    await consumerStarted;
    await vi.advanceTimersByTimeAsync(25);
    expect(fetchSignal?.aborted).toBe(false);
    caller.abort();
    await expect(stream).rejects.toMatchObject({ name: "AbortError" });
    expect(fetchSignal?.aborted).toBe(true);
  });

  it("limits only the connection handshake with the configured timeout", async () => {
    vi.useFakeTimers();
    const fetchImpl: FetchFn = (_input, init) => new Promise((_resolve, reject) => {
      init?.signal?.addEventListener("abort", () => {
        reject(new DOMException("Aborted", "AbortError"));
      }, { once: true });
    });
    const client = new ApiClient({
      getAccessToken: () => "access-token",
      setAccessToken: () => undefined,
      fetchImpl,
      defaultTimeoutMs: 25,
    });
    const stream = client.withEventStream("/events", {}, () => Promise.resolve());
    const assertion = expect(stream).rejects.toMatchObject({
      status: 408,
      code: "EVENT_STREAM_TIMEOUT",
    });

    await vi.advanceTimersByTimeAsync(25);
    await assertion;
  });

  it("rejects non-SSE success responses before invoking the consumer", async () => {
    const consumer = vi.fn(() => Promise.resolve());
    const client = new ApiClient({
      getAccessToken: () => "access-token",
      setAccessToken: () => undefined,
      fetchImpl: () => Promise.resolve(jsonResponse(200, { unexpected: true })),
    });

    await expect(client.withEventStream("/events", {}, consumer)).rejects.toMatchObject({
      status: 200,
      code: "INVALID_EVENT_STREAM_RESPONSE",
    });
    expect(consumer).not.toHaveBeenCalled();
  });

  it("clears the local session when refresh cannot recover a 401", async () => {
    let token: string | null = "stale-token";
    const onSessionCleared = vi.fn();
    const fetchImpl = vi.fn<FetchFn>((input) => Promise.resolve(
      requestUrl(input).endsWith("/api/v1/auth/web/refresh")
        ? jsonResponse(401, { code: "REFRESH_REJECTED", message: "expired" })
        : jsonResponse(401, { code: "UNAUTHORIZED", message: "expired" }),
    ));
    const client = new ApiClient({
      getAccessToken: () => token,
      setAccessToken: (value) => {
        token = value;
      },
      onSessionCleared,
      fetchImpl,
    });

    await expect(client.withEventStream("/events", {}, () => Promise.resolve()))
      .rejects.toMatchObject({ status: 401, code: "UNAUTHORIZED" });
    expect(token).toBeNull();
    expect(onSessionCleared).toHaveBeenCalledOnce();
  });
});
