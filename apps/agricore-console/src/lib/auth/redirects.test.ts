import { describe, expect, it } from "vitest";

import { sanitizeInternalRedirect } from "./redirects";

describe("sanitizeInternalRedirect", () => {
  it("keeps a normalized internal path, query, and fragment", () => {
    expect(sanitizeInternalRedirect("/farms?tab=plots#today")).toBe(
      "/farms?tab=plots#today",
    );
  });

  it.each([
    "https://attacker.example/steal",
    "//attacker.example/steal",
    "/\\attacker.example/steal",
    "/%2F%2Fattacker.example/steal",
    "/%5Cattacker.example/steal",
    " /farms",
    "/farms\n",
    "/login?redirect=/farms",
  ])("rejects unsafe redirect %s", (redirect) => {
    expect(sanitizeInternalRedirect(redirect)).toBeUndefined();
  });

  it("rejects non-string search values", () => {
    expect(sanitizeInternalRedirect(["/farms"])).toBeUndefined();
  });
});
