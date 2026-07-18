import { describe, expect, it } from "vitest";

import { hasAnyRole, visibleNavItems } from "./roles";

describe("roles", () => {
  it("grants SYSTEM_ADMIN all role-gated items", () => {
    const items = visibleNavItems(["SYSTEM_ADMIN"]);
    expect(items.map((item) => item.id)).toContain("admin");
    expect(items.map((item) => item.id)).toContain("inventory");
  });

  it("hides admin from non-admins", () => {
    const items = visibleNavItems(["FIELD_WORKER"]);
    expect(items.map((item) => item.id)).not.toContain("admin");
    expect(hasAnyRole(["FIELD_WORKER"], ["SYSTEM_ADMIN"])).toBe(false);
  });

  it("shows inventory to warehouse managers", () => {
    expect(hasAnyRole(["WAREHOUSE_MANAGER"], ["WAREHOUSE_MANAGER", "SALES_STAFF"])).toBe(true);
    expect(visibleNavItems(["WAREHOUSE_MANAGER"]).some((item) => item.id === "inventory")).toBe(
      true,
    );
  });
});
