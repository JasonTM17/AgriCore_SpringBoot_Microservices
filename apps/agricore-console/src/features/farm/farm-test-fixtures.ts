import { vi } from "vitest";

import type {
  FarmResponse,
  PlotResponse,
  UserResponse,
  WebAuthTokensResponse,
} from "../../lib/api/types";

const USER_ID = "10000000-0000-0000-0000-000000000001";
const FARM_A_ID = "20000000-0000-0000-0000-000000000001";
const FARM_B_ID = "20000000-0000-0000-0000-000000000002";

const user = {
  id: USER_ID,
  email: "manager@agricore.test",
  fullName: "Quản lý nông trại",
  status: "ACTIVE",
  roles: ["FARM_MANAGER"],
  lastLoginAt: null,
  createdAt: "2026-07-19T00:00:00Z",
} satisfies UserResponse;

const tokens = {
  accessToken: "access-token",
  tokenType: "Bearer",
  expiresIn: 900,
  user,
} satisfies WebAuthTokensResponse;

const farms = [
  {
    id: FARM_A_ID,
    code: "FARM-DL-01",
    name: "Nông trại Đắk Lắk",
    address: "Buôn Ma Thuột",
    province: "Đắk Lắk",
    totalAreaHa: 120.5,
    latitude: null,
    longitude: null,
    status: "ACTIVE",
    createdAt: "2026-07-19T00:00:00Z",
    updatedAt: "2026-07-19T00:00:00Z",
    version: 0,
  },
  {
    id: FARM_B_ID,
    code: "FARM-LD-01",
    name: "Nông trại Lâm Đồng",
    address: "Bảo Lộc",
    province: "Lâm Đồng",
    totalAreaHa: 80,
    latitude: null,
    longitude: null,
    status: "ACTIVE",
    createdAt: "2026-07-19T00:00:00Z",
    updatedAt: "2026-07-19T00:00:00Z",
    version: 0,
  },
] satisfies FarmResponse[];

const plotA = {
  id: "30000000-0000-0000-0000-000000000001",
  farmId: FARM_A_ID,
  areaId: null,
  code: "PLOT-A01",
  name: "Lô cà phê A01",
  areaInHectares: 12.25,
  soilType: "BASALT",
  status: "IN_USE",
  latitude: null,
  longitude: null,
  createdAt: "2026-07-19T00:00:00Z",
  updatedAt: "2026-07-19T00:00:00Z",
  version: 0,
} satisfies PlotResponse;

const plotB = {
  id: "30000000-0000-0000-0000-000000000002",
  farmId: FARM_B_ID,
  areaId: null,
  code: "PLOT-B01",
  name: "Lô chè B01",
  areaInHectares: 8,
  soilType: null,
  status: "AVAILABLE",
  latitude: null,
  longitude: null,
  createdAt: "2026-07-19T00:00:00Z",
  updatedAt: "2026-07-19T00:00:00Z",
  version: 0,
} satisfies PlotResponse;

export function page<T>(content: T[]) {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    first: true,
    last: true,
  };
}

export function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(
    new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    }),
  );
}

export function requestUrl(input: RequestInfo | URL): URL {
  const value = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
  return new URL(value, "http://agricore.test");
}

export function authenticatedFetch(farmResult: unknown = page(farms), farmStatus = 200) {
  return vi.fn((input: RequestInfo | URL) => {
    const url = requestUrl(input);
    if (url.pathname === "/api/v1/auth/web/refresh") {
      return jsonResponse(tokens);
    }
    if (url.pathname === "/api/v1/farms") {
      return jsonResponse(farmResult, farmStatus);
    }
    if (url.pathname === `/api/v1/farms/${FARM_A_ID}/plots`) {
      return jsonResponse(page([plotA]));
    }
    if (url.pathname === `/api/v1/farms/${FARM_B_ID}/plots`) {
      return jsonResponse(page([plotB]));
    }
    return Promise.resolve(new Response("not found", { status: 404 }));
  });
}
