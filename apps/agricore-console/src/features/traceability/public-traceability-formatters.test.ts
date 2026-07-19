import { describe, expect, it } from "vitest";

import {
  formatPublicDate,
  formatPublicWeight,
  publicText,
} from "./public-traceability-formatters";

describe("public traceability formatters", () => {
  it("does not invent missing public text", () => {
    expect(publicText(null)).toBe("Chưa công bố");
    expect(publicText("   ")).toBe("Chưa công bố");
    expect(publicText("  TR4  ")).toBe("TR4");
  });

  it("accepts real calendar dates and rejects normalized invalid dates", () => {
    expect(formatPublicDate("2024-02-29")).toBe("29/02/2024");
    expect(formatPublicDate("2026-02-30")).toBe("Chưa công bố");
    expect(formatPublicDate("not-a-date")).toBe("Chưa công bố");
  });

  it("only displays finite positive harvest weights", () => {
    expect(formatPublicWeight(3300.25)).toBe("3.300,25 kg");
    expect(formatPublicWeight(0)).toBe("Chưa công bố");
    expect(formatPublicWeight(-1)).toBe("Chưa công bố");
    expect(formatPublicWeight(Number.NaN)).toBe("Chưa công bố");
  });
});
