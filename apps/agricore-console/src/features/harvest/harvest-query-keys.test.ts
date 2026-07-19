import { describe, expect, it } from "vitest";

import { harvestQueryKeys } from "./harvest-query-keys";

describe("harvest query keys", () => {
  it("isolates receipt data by authenticated subject", () => {
    expect(harvestQueryKeys.detail("user-a", "harvest-1")).not.toEqual(
      harvestQueryKeys.detail("user-b", "harvest-1"),
    );
    expect(harvestQueryKeys.producer("user-a", "harvest-1")).not.toEqual(
      harvestQueryKeys.producer("user-b", "harvest-1"),
    );
  });

  it("groups event acknowledgement keys under the stable event identity", () => {
    const prefix = harvestQueryKeys.event("user-a", "event-1");
    const inventory = harvestQueryKeys.inventory("user-a", "event-1");
    const traceability = harvestQueryKeys.traceability("user-a", "event-1");

    expect(inventory.slice(0, prefix.length)).toEqual(prefix);
    expect(traceability.slice(0, prefix.length)).toEqual(prefix);
    expect(inventory).not.toEqual(traceability);
  });
});
