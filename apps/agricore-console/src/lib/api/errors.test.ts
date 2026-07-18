import { describe, expect, it } from "vitest";

import { parseApiError } from "./errors";

describe("parseApiError", () => {
  it("captures gateway correlation and trace identifiers from non-JSON failures", async () => {
    const response = new Response("upstream unavailable", {
      status: 502,
      statusText: "Bad Gateway",
      headers: {
        "Content-Type": "text/plain",
        "X-Correlation-ID": "corr-123",
        "X-Trace-ID": "trace-456",
      },
    });

    await expect(parseApiError(response)).resolves.toMatchObject({
      status: 502,
      code: "UNKNOWN_ERROR",
      message: "Bad Gateway",
      correlationId: "corr-123",
      traceId: "trace-456",
    });
  });
});
