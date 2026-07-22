import { describe, expect, it } from "vitest";

import { extractAssistantCitations } from "./assistant-citations";

describe("assistant citations", () => {
  it("keeps valid citation IDs in first-seen order and removes duplicates", () => {
    expect(extractAssistantCitations(
      "Mùa vụ [FARM-1] ổn định; [CYCLE-2] và [FARM-1].",
    )).toEqual(["FARM-1", "CYCLE-2"]);
  });

  it("ignores malformed or case-sensitive citation markers", () => {
    expect(extractAssistantCitations("[farm-1] [FARM_] [FARM-1] [FARM-1-EXTRA]"))
      .toEqual(["FARM-1", "FARM-1-EXTRA"]);
  });

  it("bounds rendered citation metadata", () => {
    const content = Array.from({ length: 40 }, (_, index) => `[FARM-${index + 1}]`).join(" ");
    expect(extractAssistantCitations(content)).toHaveLength(25);
  });
});
