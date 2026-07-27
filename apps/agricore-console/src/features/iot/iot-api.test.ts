import { describe, expect, it, vi } from "vitest";

import { ApiClient } from "../../lib/api/client";
import { ingestIotReading, registerIotDevice } from "./iot-api";

type FetchFn = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

describe("iot API", () => {
  it("forwards registration and reading requests to the gateway", async () => {
    const fetchImpl: FetchFn = vi.fn(() => Promise.resolve(new Response("{}", { status: 201 })));
    const api = new ApiClient({ getAccessToken: () => "token", setAccessToken: () => undefined, fetchImpl });
    const registration = { deviceCode: "SENSOR-A3", plotId: "plot-id", name: "Độ ẩm đất A3" };
    const reading = { deviceCode: "SENSOR-A3", metricType: "SOIL_MOISTURE", metricValue: 28.6, unit: "%", recordedAt: null };
    await registerIotDevice(api, registration);
    await ingestIotReading(api, reading);
    expect(vi.mocked(fetchImpl).mock.calls.map(([input, init]) => [input, init?.method])).toEqual([
      ["/api/v1/iot/devices", "POST"],
      ["/api/v1/iot/readings", "POST"],
    ]);
    expect(vi.mocked(fetchImpl).mock.calls[0]?.[1]?.body).toBe(JSON.stringify(registration));
    expect(vi.mocked(fetchImpl).mock.calls[1]?.[1]?.body).toBe(JSON.stringify(reading));
  });
});
