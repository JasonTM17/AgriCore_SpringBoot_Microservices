import { describe, expect, it, vi } from "vitest";

import { ApiClient } from "../../lib/api/client";
import type { ChangeStageRequest } from "../../lib/api/types";
import { changeCropCycleStage, getCropCycle } from "./crop-cycle-api";

type FetchFn = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

function jsonResponse(body: unknown): Promise<Response> {
  return Promise.resolve(
    new Response(JSON.stringify(body), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }),
  );
}

function client(fetchImpl: FetchFn): ApiClient {
  return new ApiClient({
    getAccessToken: () => "access-token",
    setAccessToken: () => undefined,
    fetchImpl,
  });
}

describe("crop-cycle API", () => {
  it("encodes the cycle ID and forwards the detail request signal", async () => {
    let requestSignal: AbortSignal | undefined;
    const fetchImpl: FetchFn = vi.fn(
      (_input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
        requestSignal = init?.signal ?? undefined;
        return jsonResponse({});
      },
    );
    const signal = new AbortController().signal;

    await getCropCycle(client(fetchImpl), "cycle/id?part", signal);

    expect(fetchImpl).toHaveBeenCalledOnce();
    const [input, init] = vi.mocked(fetchImpl).mock.calls[0] ?? [];
    expect(input).toBe("/api/v1/crop-cycles/cycle%2Fid%3Fpart");
    expect(init?.method).toBe("GET");
    expect(requestSignal).toBeInstanceOf(AbortSignal);
  });

  it("posts the typed stage request once with an encoded cycle ID and signal", async () => {
    let requestSignal: AbortSignal | undefined;
    const fetchImpl: FetchFn = vi.fn(
      (_input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
        requestSignal = init?.signal ?? undefined;
        return jsonResponse({});
      },
    );
    const signal = new AbortController().signal;
    const request: ChangeStageRequest = { stage: "HARVESTING", notes: "ready" };

    await changeCropCycleStage(client(fetchImpl), "cycle/id", request, signal);

    expect(fetchImpl).toHaveBeenCalledOnce();
    const [input, init] = vi.mocked(fetchImpl).mock.calls[0] ?? [];
    expect(input).toBe("/api/v1/crop-cycles/cycle%2Fid/stage");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe(JSON.stringify(request));
    expect(requestSignal).toBeInstanceOf(AbortSignal);
  });
});
