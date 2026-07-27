export type FetchFn = (
  input: RequestInfo | URL,
  init?: RequestInit,
) => Promise<Response>;

export function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export function streamResponse(body = "data: ready\n\n"): Response {
  return new Response(body, {
    status: 200,
    headers: { "Content-Type": "text/event-stream; charset=utf-8" },
  });
}

export function refreshResponse(): Response {
  return jsonResponse(200, {
    accessToken: "fresh-token",
    tokenType: "Bearer",
    expiresIn: 900,
    user: {
      id: "11111111-1111-1111-1111-111111111111",
      email: "user@agricore.test",
      fullName: "User",
      status: "ACTIVE",
      roles: ["FIELD_WORKER"],
      lastLoginAt: null,
      createdAt: "2026-07-20T00:00:00Z",
    },
  });
}

export function requestUrl(input: RequestInfo | URL): string {
  if (typeof input === "string") return input;
  if (input instanceof URL) return input.toString();
  return input.url;
}
