import http from "k6/http";
import { check, fail, sleep } from "k6";

const baseUrl = (__ENV.BASE_URL || "http://localhost:3000").replace(/\/+$/, "");
const traceabilityCode = (__ENV.TRACEABILITY_CODE || "").trim();
const virtualUsers = positiveInteger(__ENV.VUS, 10, "VUS");
const pacingSeconds = nonNegativeNumber(__ENV.PACING_SECONDS, 0.1, "PACING_SECONDS");
const p95Milliseconds = positiveInteger(__ENV.P95_MS, 750, "P95_MS");
const p99Milliseconds = positiveInteger(__ENV.P99_MS, 1500, "P99_MS");

if (!traceabilityCode) {
  throw new Error("TRACEABILITY_CODE is required; use a real persisted public projection");
}

const targetUrl =
  `${baseUrl}/public/api/v1/traceability/${encodeURIComponent(traceabilityCode)}`;
const requestParams = {
  headers: {
    Accept: "application/json",
    "User-Agent": "agricore-k6-traceability-smoke/1.0",
  },
  tags: { endpoint: "public-traceability" },
};

export const options = {
  discardResponseBodies: true,
  scenarios: {
    publicTraceabilityReads: {
      executor: "constant-vus",
      vus: virtualUsers,
      duration: __ENV.DURATION || "15s",
      gracefulStop: "5s",
    },
  },
  thresholds: {
    checks: ["rate>0.99"],
    http_req_failed: ["rate<0.01"],
    "http_req_duration{endpoint:public-traceability}": [
      `p(95)<${p95Milliseconds}`,
      `p(99)<${p99Milliseconds}`,
    ],
  },
};

export function setup() {
  const response = http.get(targetUrl, requestParams);
  const reachable = check(response, {
    "warm-up returns the persisted traceability projection": (result) =>
      result.status === 200,
  });

  if (!reachable) {
    fail(`Warm-up failed for ${targetUrl}: HTTP ${response.status}`);
  }
}

export default function publicTraceabilityRead() {
  const response = http.get(targetUrl, requestParams);
  check(response, {
    "traceability lookup returns 200": (result) => result.status === 200,
    "traceability response is JSON": (result) =>
      String(result.headers["Content-Type"] || "").includes("application/json"),
  });

  if (pacingSeconds > 0) {
    sleep(pacingSeconds);
  }
}

function positiveInteger(rawValue, fallback, name) {
  const parsed = Number.parseInt(rawValue || String(fallback), 10);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return parsed;
}

function nonNegativeNumber(rawValue, fallback, name) {
  const parsed = Number(rawValue || String(fallback));
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error(`${name} must be a non-negative number`);
  }
  return parsed;
}
