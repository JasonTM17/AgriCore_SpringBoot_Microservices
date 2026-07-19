import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "../../app/app";
import type {
  FarmResponse,
  PlotResponse,
  UserResponse,
  WebAuthTokensResponse,
} from "../../lib/api/types";
import { jsonResponse, page, requestUrl } from "./farm-test-fixtures";

const userA = {
  id: "10000000-0000-0000-0000-000000000001",
  email: "manager-a@agricore.test",
  fullName: "Quản lý A",
  status: "ACTIVE",
  roles: ["FARM_MANAGER"],
  lastLoginAt: null,
  createdAt: "2026-07-19T00:00:00Z",
} satisfies UserResponse;

const userB = {
  ...userA,
  id: "10000000-0000-0000-0000-000000000002",
  email: "manager-b@agricore.test",
  fullName: "Quản lý B",
} satisfies UserResponse;

function farm(id: string, code: string, name: string): FarmResponse {
  return {
    id,
    code,
    name,
    address: null,
    province: null,
    totalAreaHa: 10,
    latitude: null,
    longitude: null,
    status: "ACTIVE",
    createdAt: "2026-07-19T00:00:00Z",
    updatedAt: "2026-07-19T00:00:00Z",
    version: 0,
  };
}

function plot(id: string, farmId: string, code: string, name: string): PlotResponse {
  return {
    id,
    farmId,
    areaId: null,
    code,
    name,
    areaInHectares: 2,
    soilType: null,
    status: "AVAILABLE",
    latitude: null,
    longitude: null,
    createdAt: "2026-07-19T00:00:00Z",
    updatedAt: "2026-07-19T00:00:00Z",
    version: 0,
  };
}

const farmA = farm("20000000-0000-0000-0000-000000000001", "FARM-A", "Nông trại A");
const farmB = farm("20000000-0000-0000-0000-000000000002", "FARM-B", "Nông trại B");
const plotA = plot("30000000-0000-0000-0000-000000000001", farmA.id, "PLOT-A", "Lô A");
const plotB = plot("30000000-0000-0000-0000-000000000002", farmB.id, "PLOT-B", "Lô B");

function tokens(accessToken: string, user: UserResponse): WebAuthTokensResponse {
  return { accessToken, tokenType: "Bearer", expiresIn: 900, user };
}

describe("Farm query session isolation", () => {
  beforeEach(() => {
    window.history.pushState({}, "", "/farms");
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("never renders user A farm data after logout and login as user B", async () => {
    const farmAuthorizations: Array<string | null> = [];
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
        farmAuthorizations.push(authorization);
        return authorization === "Bearer token-b"
          ? jsonResponse(page([farmB]))
          : jsonResponse(page([farmA]));
      }
      if (url.pathname === `/api/v1/farms/${farmA.id}/plots`) {
        return jsonResponse(page([plotA]));
      }
      if (url.pathname === `/api/v1/farms/${farmB.id}/plots`) {
        return jsonResponse(page([plotB]));
      }
      return Promise.resolve(new Response("not found", { status: 404 }));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    expect(await screen.findByText("Lô A")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Đăng xuất" }));
    await screen.findByRole("heading", { name: "Chào mừng trở lại" });

    fireEvent.change(screen.getByLabelText("Email"), { target: { value: userB.email } });
    fireEvent.change(screen.getByLabelText("Mật khẩu"), { target: { value: "Secret123!" } });
    fireEvent.click(screen.getByRole("button", { name: "Đăng nhập" }));

    fireEvent.click(await screen.findByRole("link", { name: "Nông trại" }));
    expect(await screen.findByText("Lô B")).toBeInTheDocument();
    expect(screen.queryByText("Nông trại A")).not.toBeInTheDocument();
    expect(farmAuthorizations).toContain("Bearer token-a");
    expect(farmAuthorizations).toContain("Bearer token-b");
  });
});
