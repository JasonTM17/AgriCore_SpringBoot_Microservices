import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
import type { UserResponse, WebAuthTokensResponse } from "../../lib/api/types";
import {
  cycleA,
  cycleB,
  farmA,
  farmB,
  jsonResponse,
  page,
  requestUrl,
} from "./crop-cycle-test-fixtures";

const userA = {
  id: "10000000-0000-0000-0000-000000000001",
  email: "agronomist-a@agricore.test",
  fullName: "Kỹ thuật viên A",
  status: "ACTIVE",
  roles: ["AGRONOMIST"],
  permissions: [],
  lastLoginAt: null,
  createdAt: "2026-07-19T00:00:00Z",
} satisfies UserResponse;

const userB = {
  ...userA,
  id: "10000000-0000-0000-0000-000000000002",
  email: "agronomist-b@agricore.test",
  fullName: "Kỹ thuật viên B",
} satisfies UserResponse;

const warehouseUser = {
  ...userA,
  id: "10000000-0000-0000-0000-000000000003",
  email: "warehouse@agricore.test",
  fullName: "Quản lý kho",
  roles: ["WAREHOUSE_MANAGER"],
} satisfies UserResponse;

function tokens(accessToken: string, user: UserResponse): WebAuthTokensResponse {
  return { accessToken, tokenType: "Bearer", expiresIn: 900, user };
}

describe("Crop cycle access gates", () => {
  beforeEach(() => {
    window.history.pushState({}, "", "/crop-cycles");
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("does not reuse user A cycle cache after logout and login as user B", async () => {
    const cycleAuthorizations: Array<string | null> = [];
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      const authorization = new Headers(init?.headers).get("Authorization");

      if (url.pathname === "/api/v1/auth/web/refresh") {
        return jsonResponse(tokens("token-a", userA));
      }
      if (url.pathname === "/api/v1/auth/web/logout") {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      if (url.pathname === "/api/v1/auth/web/login") {
        return jsonResponse(tokens("token-b", userB));
      }
      if (url.pathname === "/api/v1/farms") {
        return authorization === "Bearer token-b"
          ? jsonResponse(page([farmB]))
          : jsonResponse(page([farmA]));
      }
      if (url.pathname === `/api/v1/farms/${farmA.id}`) {
        return jsonResponse(farmA);
      }
      if (url.pathname === `/api/v1/farms/${farmB.id}`) {
        return jsonResponse(farmB);
      }
      if (url.pathname === "/api/v1/crop-cycles") {
        cycleAuthorizations.push(authorization);
        return authorization === "Bearer token-b"
          ? jsonResponse(page([cycleB]))
          : jsonResponse(page([cycleA]));
      }
      return Promise.resolve(new Response("not found", { status: 404 }));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByText(cycleA.code)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Đăng xuất" }));
    await screen.findByRole("heading", { name: "Chào mừng trở lại" });

    fireEvent.change(screen.getByLabelText("Email"), { target: { value: userB.email } });
    fireEvent.change(screen.getByLabelText("Mật khẩu"), { target: { value: "Secret123!" } });
    fireEvent.click(screen.getByRole("button", { name: "Đăng nhập" }));
    fireEvent.click(await screen.findByRole("link", { name: "Mùa vụ & công việc" }));

    expect(await screen.findByText(cycleB.code)).toBeInTheDocument();
    expect(screen.queryByText(cycleA.code)).not.toBeInTheDocument();
    expect(cycleAuthorizations).toContain("Bearer token-a");
    expect(cycleAuthorizations).toContain("Bearer token-b");
  });

  it("does not mount crop-cycle queries for a disallowed warehouse role", async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = requestUrl(input);
      if (url.pathname === "/api/v1/auth/web/refresh") {
        return jsonResponse(tokens("warehouse-token", warehouseUser));
      }
      if (url.pathname === "/api/v1/farms") {
        return jsonResponse(page([]));
      }
      return Promise.resolve(new Response("not found", { status: 404 }));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByText("Không có quyền truy cập")).toBeInTheDocument();
    expect(fetchMock.mock.calls
      .map(([input]) => requestUrl(input))
      .some((url) => url.pathname === "/api/v1/crop-cycles"))
      .toBe(false);
  });
});
