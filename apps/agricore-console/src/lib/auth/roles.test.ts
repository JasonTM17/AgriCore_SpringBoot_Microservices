import { describe, expect, it } from "vitest";

import { hasAnyPermission, hasAnyRole, visibleNavItems } from "./roles";

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

  it("shows IoT ingestion to field workers", () => {
    expect(visibleNavItems(["FIELD_WORKER"]).some((item) => item.id === "iot")).toBe(true);
  });

  it("uses effective permissions when the session provides them", () => {
    const items = visibleNavItems(["SYSTEM_ADMIN"], ["FARM_READ", "ASSISTANT_USE"]);

    expect(items.map((item) => item.id)).toEqual(["dashboard", "farms", "assistant"]);
    expect(hasAnyPermission(["FARM_READ"], ["FARM_READ"])).toBe(true);
    expect(hasAnyPermission([], ["FARM_READ"])).toBe(false);
  });

  it("does not expose role-matched navigation after a permission is revoked", () => {
    expect(visibleNavItems(["WAREHOUSE_MANAGER"], []).map((item) => item.id)).toEqual(["dashboard"]);
  });
});
