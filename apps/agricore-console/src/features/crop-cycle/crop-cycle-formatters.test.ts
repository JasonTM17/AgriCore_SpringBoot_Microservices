import { describe, expect, it } from "vitest";

import {
  cycleStageLabel,
  cycleStatusLabel,
  formatCycleDate,
  formatCycleDateRange,
  shortResourceId,
} from "./crop-cycle-formatters";

describe("crop cycle formatters", () => {
  it("maps contract status and stage codes to Vietnamese labels", () => {
    expect(cycleStageLabel("LAND_PREPARATION")).toBe("Chuẩn bị đất");
    expect(cycleStageLabel("HARVESTING")).toBe("Đang thu hoạch");
    expect(cycleStatusLabel("ACTIVE")).toBe("Đang hoạt động");
    expect(cycleStatusLabel("CANCELLED")).toBe("Đã hủy");
  });

  it("formats date-only contract values without local timezone drift", () => {
    expect(formatCycleDate("2026-03-01")).toBe("01/03/2026");
    expect(formatCycleDate(null)).toBe("Chưa cập nhật");
    expect(formatCycleDateRange("2026-03-01", "2026-11-30")).toBe(
      "01/03/2026 – 30/11/2026",
    );
  });

  it("uses the distinguishing suffix for compact UUID display", () => {
    expect(shortResourceId("30000000-0000-0000-0000-000000000001")).toBe("00000001");
  });
});
