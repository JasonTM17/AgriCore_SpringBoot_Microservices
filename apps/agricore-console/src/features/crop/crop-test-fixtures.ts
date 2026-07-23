import { vi } from "vitest";

import type { CropResponse, UserResponse, WebAuthTokensResponse } from "../../lib/api/types";

const user = {
  id: "10000000-0000-0000-0000-000000000001",
  email: "agronomist@agricore.test",
  fullName: "Kỹ thuật viên nông học",
  status: "ACTIVE",
  roles: ["AGRONOMIST"],
  permissions: [],
  lastLoginAt: null,
  createdAt: "2026-07-19T00:00:00Z",
} satisfies UserResponse;

const tokens = {
  accessToken: "crop-access-token",
  tokenType: "Bearer",
  expiresIn: 900,
  user,
} satisfies WebAuthTokensResponse;

export const crops: CropResponse[] = [
  {
    id: "40000000-0000-0000-0000-000000000001",
    code: "COFFEE_ROBUSTA",
    name: "Cà phê Robusta",
    scientificName: "Coffea canephora",
    category: "PERENNIAL",
    growthDaysMin: 240,
    growthDaysMax: 300,
    tempMinC: 18,
    tempMaxC: 30,
    humidityMinPct: 60,
    humidityMaxPct: 80,
    phMin: 5.5,
    phMax: 6.5,
    expectedYieldPerHa: 2.4,
    yieldUnit: "TON",
    description: "Cây cà phê chủ lực vùng cao.",
    createdAt: "2026-07-19T00:00:00Z",
  },
  {
    id: "40000000-0000-0000-0000-000000000002",
    code: "RICE_ST25",
    name: "Lúa ST25",
    scientificName: null,
    category: "ANNUAL",
    growthDaysMin: null,
    growthDaysMax: 120,
    tempMinC: null,
    tempMaxC: null,
    humidityMinPct: null,
    humidityMaxPct: null,
    phMin: 5,
    phMax: 6.5,
    expectedYieldPerHa: null,
    yieldUnit: null,
    description: null,
    createdAt: "2026-07-19T00:00:00Z",
  },
];

export function page<T>(content: T[], pageNumber = 0, totalPages = content.length > 0 ? 1 : 0) {
  return {
    content,
    page: pageNumber,
    size: 20,
    totalElements: totalPages === 0 ? 0 : totalPages * 2,
    totalPages,
    first: pageNumber === 0,
    last: pageNumber + 1 >= totalPages,
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

export function authenticatedFetch(cropResult: unknown = page(crops)) {
  return vi.fn((input: RequestInfo | URL) => {
    const url = requestUrl(input);
    if (url.pathname === "/api/v1/auth/web/refresh") {
      return jsonResponse(tokens);
    }
    if (url.pathname === "/api/v1/farms") {
      return jsonResponse(page([]));
    }
    if (url.pathname === "/api/v1/crops") {
      return jsonResponse(cropResult);
    }
    return Promise.resolve(new Response("not found", { status: 404 }));
  });
}
