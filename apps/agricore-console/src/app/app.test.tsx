import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "./app";

function requestUrl(input: RequestInfo | URL): string {
  if (typeof input === "string") {
    return input;
  }
  if (input instanceof URL) {
    return input.toString();
  }
  return input.url;
}

describe("App auth shell", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) => {
        const url = requestUrl(input);
        if (url.includes("/api/v1/auth/web/refresh")) {
          return Promise.resolve(
            new Response(
              JSON.stringify({
                status: 401,
                error: "Unauthorized",
                code: "INVALID_REFRESH_TOKEN",
                message: "missing",
              }),
              { status: 401, headers: { "Content-Type": "application/json" } },
            ),
          );
        }
        return Promise.resolve(new Response("not found", { status: 404 }));
      }),
    );
  });

  it("renders the Vietnamese login experience for anonymous sessions", async () => {
    window.history.pushState({}, "", "/login");
    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Chào mừng trở lại" })).toBeInTheDocument();
    });
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
    expect(screen.getByLabelText("Mật khẩu")).toBeInTheDocument();
  });
});
