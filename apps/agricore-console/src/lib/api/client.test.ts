import { describe, expect, it, vi } from "vitest";

import { ApiClient } from "./client";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function requestUrl(input: RequestInfo | URL): string {
  if (typeof input === "string") {
    return input;
  }
  if (input instanceof URL) {
    return input.toString();
  }
  return input.url;
}

type FetchFn = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

describe("ApiClient", () => {
  it("stores access token from web login and never expects refresh in body", async () => {
    let stored: string | null = null;
    const fetchImpl: FetchFn = vi.fn(() =>
      Promise.resolve(
        jsonResponse(200, {
          accessToken: "access-1",
          tokenType: "Bearer",
          expiresIn: 900,
          user: {
            id: "11111111-1111-1111-1111-111111111111",
            email: "a@agricore.test",
            fullName: "A",
            status: "ACTIVE",
            roles: ["FIELD_WORKER"],
            lastLoginAt: null,
            createdAt: "2026-07-18T00:00:00Z",
          },
        }),
      ),
    );

    const client = new ApiClient({
      getAccessToken: () => stored,
      setAccessToken: (token) => {
        stored = token;
      },
      fetchImpl,
    });

    const result = await client.webLogin({ email: "a@agricore.test", password: "Secret123!" });
    expect(result.accessToken).toBe("access-1");
    expect(stored).toBe("access-1");
    expect(fetchImpl).toHaveBeenCalledOnce();
    const mock = vi.mocked(fetchImpl);
    const firstCall = mock.mock.calls[0];
    expect(firstCall).toBeDefined();
    expect(firstCall?.[1]?.credentials).toBe("include");
    expect(firstCall?.[1]?.method).toBe("POST");
  });

  it("single-flights concurrent 401 refresh attempts", async () => {
    let stored: string | null = "stale";
    let refreshCalls = 0;
    let meCalls = 0;

    const fetchImpl: FetchFn = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.endsWith("/api/v1/auth/web/refresh")) {
        refreshCalls += 1;
        return new Promise<Response>((resolve) => {
          setTimeout(() => {
            stored = "fresh";
            resolve(
              jsonResponse(200, {
                accessToken: "fresh",
                tokenType: "Bearer",
                expiresIn: 900,
                user: {
                  id: "11111111-1111-1111-1111-111111111111",
                  email: "a@agricore.test",
                  fullName: "A",
                  status: "ACTIVE",
                  roles: ["FIELD_WORKER"],
                  lastLoginAt: null,
                  createdAt: "2026-07-18T00:00:00Z",
                },
              }),
            );
          }, 20);
        });
      }

      if (url.endsWith("/api/v1/users/me")) {
        meCalls += 1;
        const auth = new Headers(init?.headers).get("Authorization");
        if (auth === "Bearer fresh") {
          return Promise.resolve(
            jsonResponse(200, {
              id: "11111111-1111-1111-1111-111111111111",
              email: "a@agricore.test",
              fullName: "A",
              status: "ACTIVE",
              roles: ["FIELD_WORKER"],
              lastLoginAt: null,
              createdAt: "2026-07-18T00:00:00Z",
            }),
          );
        }
        return Promise.resolve(
          jsonResponse(401, {
            status: 401,
            error: "Unauthorized",
            code: "UNAUTHORIZED",
            message: "expired",
          }),
        );
      }

      return Promise.resolve(
        jsonResponse(404, { status: 404, error: "Not Found", code: "NOT_FOUND", message: "nope" }),
      );
    });

    const client = new ApiClient({
      getAccessToken: () => stored,
      setAccessToken: (token) => {
        stored = token;
      },
      fetchImpl,
    });

    const [first, second] = await Promise.all([client.getCurrentUser(), client.getCurrentUser()]);
    expect(first.email).toBe("a@agricore.test");
    expect(second.email).toBe("a@agricore.test");
    expect(refreshCalls).toBe(1);
    expect(meCalls).toBeGreaterThanOrEqual(3);
    expect(stored).toBe("fresh");
  });

  it("single-flights concurrent explicit web refresh attempts", async () => {
    let stored: string | null = null;
    let refreshCalls = 0;

    const fetchImpl: FetchFn = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (!url.endsWith("/api/v1/auth/web/refresh")) {
        return Promise.resolve(
          jsonResponse(404, {
            status: 404,
            error: "Not Found",
            code: "NOT_FOUND",
            message: "nope",
          }),
        );
      }

      refreshCalls += 1;
      return new Promise<Response>((resolve) => {
        setTimeout(() => {
          resolve(
            jsonResponse(200, {
              accessToken: "fresh",
              tokenType: "Bearer",
              expiresIn: 900,
              user: {
                id: "11111111-1111-1111-1111-111111111111",
                email: "a@agricore.test",
                fullName: "A",
                status: "ACTIVE",
                roles: ["FIELD_WORKER"],
                lastLoginAt: null,
                createdAt: "2026-07-18T00:00:00Z",
              },
            }),
          );
        }, 20);
      });
    });

    const client = new ApiClient({
      getAccessToken: () => stored,
      setAccessToken: (token) => {
        stored = token;
      },
      fetchImpl,
    });

    const [first, second] = await Promise.all([client.webRefresh(), client.webRefresh()]);

    expect(first.accessToken).toBe("fresh");
    expect(second.accessToken).toBe("fresh");
    expect(refreshCalls).toBe(1);
    expect(stored).toBe("fresh");
  });

  it("does not restore a refresh result after logout", async () => {
    let stored: string | null = "stale";
    let resolveRefresh: ((response: Response) => void) | undefined;
    const fetchImpl: FetchFn = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.endsWith("/api/v1/auth/web/refresh")) {
        return new Promise<Response>((resolve) => {
          resolveRefresh = resolve;
        });
      }
      if (url.endsWith("/api/v1/auth/web/logout")) {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(jsonResponse(404, {}));
    });
    const client = new ApiClient({
      getAccessToken: () => stored,
      setAccessToken: (token) => {
        stored = token;
      },
      fetchImpl,
    });

    const refresh = client.webRefresh();
    await client.webLogout();
    resolveRefresh?.(authTokensResponse("refreshed-after-logout"));

    await expect(refresh).rejects.toThrow("superseded");
    expect(stored).toBeNull();
  });

  it("does not let an older refresh overwrite a newer login", async () => {
    let stored: string | null = "stale";
    let resolveRefresh: ((response: Response) => void) | undefined;
    const fetchImpl: FetchFn = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.endsWith("/api/v1/auth/web/refresh")) {
        return new Promise<Response>((resolve) => {
          resolveRefresh = resolve;
        });
      }
      if (url.endsWith("/api/v1/auth/web/login")) {
        return Promise.resolve(authTokensResponse("new-login"));
      }
      return Promise.resolve(jsonResponse(404, {}));
    });
    const client = new ApiClient({
      getAccessToken: () => stored,
      setAccessToken: (token) => {
        stored = token;
      },
      fetchImpl,
    });

    const refresh = client.webRefresh();
    await client.webLogin({ email: "a@agricore.test", password: "Secret123!" });
    resolveRefresh?.(authTokensResponse("old-refresh"));

    await expect(refresh).rejects.toThrow("superseded");
    expect(stored).toBe("new-login");
  });

  it("waits for an active logout before sending a replacement login", async () => {
    let stored: string | null = "old-access";
    let resolveLogout: ((response: Response) => void) | undefined;
    let resolveLogin: ((response: Response) => void) | undefined;
    let markLoginStarted: (() => void) | undefined;
    const order: string[] = [];
    const loginStarted = new Promise<void>((resolve) => {
      markLoginStarted = resolve;
    });
    const fetchImpl: FetchFn = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.endsWith("/api/v1/auth/web/logout")) {
        order.push("logout:request");
        return new Promise<Response>((resolve) => {
          resolveLogout = resolve;
        });
      }
      if (url.endsWith("/api/v1/auth/web/login")) {
        order.push("login:request");
        markLoginStarted?.();
        return new Promise<Response>((resolve) => {
          resolveLogin = resolve;
        });
      }
      return Promise.resolve(jsonResponse(404, {}));
    });
    const client = new ApiClient({
      getAccessToken: () => stored,
      setAccessToken: (token) => {
        stored = token;
      },
      fetchImpl,
    });

    const logout = client.webLogout();
    const login = client.webLogin({ email: "a@agricore.test", password: "Secret123!" });

    expect(stored).toBeNull();
    await vi.waitFor(() => expect(order).toEqual(["logout:request"]));

    order.push("logout:response");
    resolveLogout?.(new Response(null, { status: 204 }));
    await loginStarted;

    expect(order).toEqual(["logout:request", "logout:response", "login:request"]);
    expect(stored).toBeNull();

    order.push("login:response");
    resolveLogin?.(authTokensResponse("replacement-access"));
    const [, tokens] = await Promise.all([logout, login]);

    expect(tokens.accessToken).toBe("replacement-access");
    expect(stored).toBe("replacement-access");
    expect(order).toEqual([
      "logout:request",
      "logout:response",
      "login:request",
      "login:response",
    ]);
  });

  it("maps API error bodies", async () => {
    const fetchImpl: FetchFn = () =>
      Promise.resolve(
        jsonResponse(401, {
          status: 401,
          error: "Unauthorized",
          code: "INVALID_CREDENTIALS",
          message: "Invalid email or password",
        }),
      );

    const client = new ApiClient({
      getAccessToken: () => null,
      setAccessToken: () => undefined,
      fetchImpl,
    });

    await expect(client.webLogin({ email: "x@y.z", password: "bad-password" })).rejects.toMatchObject(
      {
        code: "INVALID_CREDENTIALS",
        status: 401,
      },
    );
  });
});

function authTokensResponse(accessToken: string): Response {
  return jsonResponse(200, {
    accessToken,
    tokenType: "Bearer",
    expiresIn: 900,
    user: {
      id: "11111111-1111-1111-1111-111111111111",
      email: "a@agricore.test",
      fullName: "A",
      status: "ACTIVE",
      roles: ["FIELD_WORKER"],
      lastLoginAt: null,
      createdAt: "2026-07-18T00:00:00Z",
    },
  });
}
