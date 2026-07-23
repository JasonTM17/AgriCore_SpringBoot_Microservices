import { vi } from "vitest";

import type {
  CropCycleResponse,
  FarmResponse,
  UserResponse,
  WebAuthTokensResponse,
} from "../../lib/api/types";

export const farmA = {
  id: "20000000-0000-0000-0000-000000000001",
  code: "FARM-DL-01",
  enterpriseId: null,
  name: "Nông trại Đắk Lắk",
  address: null,
  province: "Đắk Lắk",
  totalAreaHa: 120,
  latitude: null,
  longitude: null,
  status: "ACTIVE",
  createdAt: "2026-07-19T00:00:00Z",
  updatedAt: "2026-07-19T00:00:00Z",
  version: 0,
} satisfies FarmResponse;

export const farmB = {
  ...farmA,
  id: "20000000-0000-0000-0000-000000000002",
  code: "FARM-LD-01",
  name: "Nông trại Lâm Đồng",
  province: "Lâm Đồng",
} satisfies FarmResponse;

const user = {
  id: "10000000-0000-0000-0000-000000000001",
  email: "agronomist@agricore.test",
  fullName: "Kỹ thuật viên nông học",
  status: "ACTIVE",
  roles: ["AGRONOMIST"],
  lastLoginAt: null,
  createdAt: "2026-07-19T00:00:00Z",
} satisfies UserResponse;

const tokens = {
  accessToken: "cycle-access-token",
  tokenType: "Bearer",
  expiresIn: 900,
  user,
} satisfies WebAuthTokensResponse;

function cycle(farmId: string, suffix: string): CropCycleResponse {
  return {
    id: `50000000-0000-0000-0000-00000000000${suffix}`,
    code: `CYCLE-${suffix}`,
    farmId,
    plotId: `30000000-0000-0000-0000-00000000000${suffix}`,
    cropId: `40000000-0000-0000-0000-00000000000${suffix}`,
    cropVarietyId: null,
    plannedStartDate: "2026-03-01",
    plannedEndDate: "2026-11-30",
    actualStartDate: suffix === "1" ? "2026-03-02" : null,
    actualEndDate: null,
    stage: suffix === "1" ? "GROWING" : "PLANNED",
    status: suffix === "1" ? "ACTIVE" : "DRAFT",
    notes: suffix === "1" ? "Mùa cà phê chính" : null,
    createdAt: "2026-07-19T00:00:00Z",
    updatedAt: "2026-07-19T00:00:00Z",
    version: 0,
  };
}

export const cycleA = cycle(farmA.id, "1");
export const cycleB = cycle(farmB.id, "2");

export function page<T>(content: T[], pageNumber = 0, totalPages = content.length > 0 ? 1 : 0) {
  return {
    content,
    page: pageNumber,
    size: 20,
    totalElements: totalPages === 0 ? 0 : Math.max(content.length, totalPages),
    totalPages,
    first: pageNumber === 0,
    last: pageNumber + 1 >= totalPages,
  };
}

export function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  }));
}

export function requestUrl(input: RequestInfo | URL): URL {
  const value = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
  return new URL(value, "http://agricore.test");
}

export function authenticatedFetch(farmsResult: unknown = page([farmA, farmB])) {
  return vi.fn((input: RequestInfo | URL) => {
    const url = requestUrl(input);
    if (url.pathname === "/api/v1/auth/web/refresh") return jsonResponse(tokens);
    if (url.pathname === "/api/v1/farms") return jsonResponse(farmsResult);
    if (url.pathname === `/api/v1/farms/${farmA.id}`) return jsonResponse(farmA);
    if (url.pathname === `/api/v1/farms/${farmB.id}`) return jsonResponse(farmB);
    if (url.pathname === "/api/v1/crop-cycles") {
      return jsonResponse(page([url.searchParams.get("farmId") === farmB.id ? cycleB : cycleA]));
    }
    if (url.pathname === "/api/v1/work-tasks") return jsonResponse(page([]));
    return Promise.resolve(new Response("not found", { status: 404 }));
  });
}
