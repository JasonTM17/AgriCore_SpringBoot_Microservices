import type { RoleCode } from "../api/types";

export const ALL_ROLES: readonly RoleCode[] = [
  "SYSTEM_ADMIN",
  "FARM_MANAGER",
  "AGRONOMIST",
  "FIELD_WORKER",
  "WAREHOUSE_MANAGER",
  "SALES_STAFF",
  "AUDITOR",
] as const;

export interface NavItem {
  id: string;
  label: string;
  to: string;
  roles: readonly RoleCode[] | "all";
  permissions?: readonly string[] | "all";
}

/** Sidebar items aligned with Stitch design package. */
export const NAV_ITEMS: readonly NavItem[] = [
  { id: "dashboard", label: "Tổng quan", to: "/", roles: "all", permissions: "all" },
  {
    id: "farms",
    label: "Nông trại",
    to: "/farms",
    roles: ["SYSTEM_ADMIN", "FARM_MANAGER", "AGRONOMIST", "FIELD_WORKER", "AUDITOR"],
    permissions: ["FARM_READ"],
  },
  {
    id: "crops",
    label: "Danh mục cây trồng",
    to: "/crops",
    roles: ["SYSTEM_ADMIN", "FARM_MANAGER", "AGRONOMIST", "FIELD_WORKER", "AUDITOR"],
    permissions: ["CROP_CATALOG_READ"],
  },
  {
    id: "cycles",
    label: "Mùa vụ & công việc",
    to: "/crop-cycles",
    roles: ["SYSTEM_ADMIN", "FARM_MANAGER", "AGRONOMIST", "FIELD_WORKER", "AUDITOR"],
    permissions: ["CROP_CYCLE_READ"],
  },
  {
    id: "harvest",
    label: "Thu hoạch",
    to: "/harvests",
    roles: ["SYSTEM_ADMIN", "FARM_MANAGER", "AGRONOMIST", "FIELD_WORKER", "WAREHOUSE_MANAGER", "AUDITOR"],
    permissions: ["HARVEST_READ"],
  },
  {
    id: "inventory",
    label: "Kho vận",
    to: "/inventory",
    roles: ["SYSTEM_ADMIN", "WAREHOUSE_MANAGER", "SALES_STAFF", "AUDITOR"],
    permissions: ["INVENTORY_READ"],
  },
  {
    id: "sales",
    label: "Bán hàng",
    to: "/sales",
    roles: ["SYSTEM_ADMIN", "SALES_STAFF", "WAREHOUSE_MANAGER", "AUDITOR"],
    permissions: ["SALES_READ"],
  },
  {
    id: "iot",
    label: "IoT",
    to: "/iot",
    roles: ["SYSTEM_ADMIN", "FARM_MANAGER", "AGRONOMIST", "FIELD_WORKER", "AUDITOR"],
    permissions: ["IOT_READ"],
  },
  {
    id: "admin",
    label: "Quản trị",
    to: "/admin/users",
    roles: ["SYSTEM_ADMIN"],
    permissions: ["IDENTITY_USER_READ"],
  },
  {
    id: "assistant",
    label: "Trợ lý",
    to: "/assistant",
    roles: "all",
    permissions: ["ASSISTANT_USE"],
  },
] as const;

export function hasAnyRole(userRoles: readonly string[], allowed: readonly RoleCode[] | "all"): boolean {
  if (allowed === "all") {
    return true;
  }
  if (userRoles.includes("SYSTEM_ADMIN")) {
    return true;
  }
  return allowed.some((role) => userRoles.includes(role));
}

export function hasAnyPermission(
  userPermissions: readonly string[],
  allowed: NavItem["permissions"],
): boolean {
  if (!allowed || allowed === "all") {
    return true;
  }
  return allowed.some((permission) => userPermissions.includes(permission));
}

export function visibleNavItems(
  userRoles: readonly string[],
  userPermissions?: readonly string[],
): NavItem[] {
  return NAV_ITEMS.filter(
    (item) =>
      hasAnyRole(userRoles, item.roles) &&
      (userPermissions === undefined || hasAnyPermission(userPermissions, item.permissions)),
  );
}
