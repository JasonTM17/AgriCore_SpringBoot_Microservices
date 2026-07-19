import { describe, expect, it, vi } from "vitest";

import { ApiClient } from "./client";

type FetchFn = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

function requestUrl(input: RequestInfo | URL): string {
  if (typeof input === "string") return input;
  if (input instanceof URL) return input.toString();
  return input.url;
}

describe("ApiClient public requests", () => {
  it("omits ambient credentials and never refreshes a rejected request", async () => {
    let refreshCalls = 0;
    const fetchImpl: FetchFn = vi.fn((input: RequestInfo | URL) => {
      if (requestUrl(input).endsWith("/api/v1/auth/web/refresh")) refreshCalls += 1;
      return Promise.resolve(new Response(JSON.stringify({
        status: 401,
        error: "Unauthorized",
        code: "PUBLIC_ROUTE_MISCONFIGURED",
        message: "unexpected auth challenge",
      }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      }));
    });
    const client = new ApiClient({
      getAccessToken: () => "test-access-token",
      setAccessToken: () => undefined,
      fetchImpl,
    });

    await expect(client.publicGet<unknown>("/public/example"))
      .rejects.toMatchObject({ status: 401, code: "PUBLIC_ROUTE_MISCONFIGURED" });

    expect(fetchImpl).toHaveBeenCalledOnce();
    const request = vi.mocked(fetchImpl).mock.calls[0]?.[1];
    expect(request?.credentials).toBe("omit");
    expect(request?.cache).toBe("no-store");
    expect(new Headers(request?.headers).has("Authorization")).toBe(false);
    expect(refreshCalls).toBe(0);
  });
});
