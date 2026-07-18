import { describe, expect, it } from "vitest";

import { LIVE_API_CAPABILITIES } from "./domain-types";

describe("LIVE_API_CAPABILITIES", () => {
  it("never marks missing list/aggregate surfaces as live truth", () => {
    expect(LIVE_API_CAPABILITIES.inventoryList).toBe(false);
    expect(LIVE_API_CAPABILITIES.salesOrderList).toBe(false);
    expect(LIVE_API_CAPABILITIES.iotDeviceList).toBe(false);
    expect(LIVE_API_CAPABILITIES.dashboardAggregate).toBe(false);
  });

  it("enables only verified controller-backed surfaces", () => {
    expect(LIVE_API_CAPABILITIES.farmsList).toBe(true);
    expect(LIVE_API_CAPABILITIES.cropCyclesList).toBe(true);
    expect(LIVE_API_CAPABILITIES.workTasksList).toBe(true);
    expect(LIVE_API_CAPABILITIES.harvestGetById).toBe(true);
    expect(LIVE_API_CAPABILITIES.inventoryGetById).toBe(true);
    expect(LIVE_API_CAPABILITIES.publicTraceByCode).toBe(true);
    expect(LIVE_API_CAPABILITIES.assistantChat).toBe(true);
  });
});
