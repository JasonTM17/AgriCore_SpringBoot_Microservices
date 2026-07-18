import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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

  it("clears the password and focuses the error summary after rejected credentials", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) => {
        const url = requestUrl(input);
        const body = url.includes("/api/v1/auth/web/login")
          ? {
              status: 401,
              error: "Unauthorized",
              code: "INVALID_CREDENTIALS",
              message: "Invalid email or password",
            }
          : {
              status: 401,
              error: "Unauthorized",
              code: "INVALID_REFRESH_TOKEN",
              message: "missing",
            };
        return Promise.resolve(
          new Response(JSON.stringify(body), {
            status: 401,
            headers: { "Content-Type": "application/json" },
          }),
        );
      }),
    );
    window.history.pushState({}, "", "/login");
    render(<App />);

    const email = await screen.findByLabelText("Email");
    const password = screen.getByLabelText("Mật khẩu");
    fireEvent.change(email, { target: { value: "worker@agricore.test" } });
    fireEvent.change(password, { target: { value: "Secret123!" } });
    fireEvent.click(screen.getByRole("button", { name: "Đăng nhập" }));

    const error = await screen.findByRole("alert");
    expect(error).toHaveTextContent("Email hoặc mật khẩu không đúng.");
    expect(error).toHaveFocus();
    expect(password).toHaveValue("");
  });

  it("focuses the first invalid field", async () => {
    window.history.pushState({}, "", "/login");
    render(<App />);

    await screen.findByRole("heading", { name: "Chào mừng trở lại" });
    fireEvent.click(screen.getByRole("button", { name: "Đăng nhập" }));

    expect(screen.getByLabelText("Email")).toHaveFocus();
    expect(screen.getByText("Email không hợp lệ")).toBeInTheDocument();
  });
});
