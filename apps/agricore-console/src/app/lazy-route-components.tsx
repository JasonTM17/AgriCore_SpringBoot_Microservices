import { lazyRouteComponent, type RouteComponent } from "@tanstack/react-router";

import { HARVEST_VIEW_ROLES } from "../features/harvest/harvest-roles";
import type { NavItem } from "../lib/auth/roles";
import { RoleGate } from "./auth-gates";

const FARM_OPERATIONS_ROLES: NavItem["roles"] = [
  "SYSTEM_ADMIN",
  "FARM_MANAGER",
  "AGRONOMIST",
  "FIELD_WORKER",
  "AUDITOR",
];
const INVENTORY_VIEW_ROLES: NavItem["roles"] = [
  "SYSTEM_ADMIN",
  "WAREHOUSE_MANAGER",
  "SALES_STAFF",
  "AUDITOR",
];
const SALES_VIEW_ROLES: NavItem["roles"] = [
  "SYSTEM_ADMIN",
  "SALES_STAFF",
  "WAREHOUSE_MANAGER",
  "AUDITOR",
];
const IOT_VIEW_ROLES: NavItem["roles"] = [
  "SYSTEM_ADMIN",
  "FARM_MANAGER",
  "AGRONOMIST",
  "FIELD_WORKER",
  "AUDITOR",
];

function withRoleGate(
  Page: RouteComponent,
  roles: NavItem["roles"],
): RouteComponent {
  const RoleGatedPage: RouteComponent = () => (
    <RoleGate roles={roles}>
      <Page />
    </RoleGate>
  );
  if (Page.preload) {
    RoleGatedPage.preload = Page.preload;
  }
  return RoleGatedPage;
}

export const lazyRouteComponents = {
  login: lazyRouteComponent(() => import("../features/auth/login-page"), "LoginPage"),
  dashboard: lazyRouteComponent(
    () => import("../features/dashboard/dashboard-page"),
    "DashboardPage",
  ),
  assistant: lazyRouteComponent(
    () => import("../features/assistant/assistant-page"),
    "AssistantPage",
  ),
  farms: withRoleGate(
    lazyRouteComponent(() => import("../features/farm/farms-page"), "FarmsPage"),
    FARM_OPERATIONS_ROLES,
  ),
  crops: withRoleGate(
    lazyRouteComponent(() => import("../features/crop/crops-page"), "CropsPage"),
    FARM_OPERATIONS_ROLES,
  ),
  cropCycles: withRoleGate(
    lazyRouteComponent(
      () => import("../features/crop-cycle/crop-cycles-page"),
      "CropCyclesPage",
    ),
    FARM_OPERATIONS_ROLES,
  ),
  cropCycleDetail: lazyRouteComponent(
    () => import("./crop-cycle-detail-route"),
    "CropCycleDetailRoute",
  ),
  harvests: withRoleGate(
    lazyRouteComponent(() => import("../features/harvest/harvest-page"), "HarvestPage"),
    HARVEST_VIEW_ROLES,
  ),
  harvestReceipt: lazyRouteComponent(
    () => import("./harvest-receipt-route"),
    "HarvestReceiptRoute",
  ),
  inventory: withRoleGate(
    lazyRouteComponent(
      () => import("../features/inventory/inventory-page"),
      "InventoryPage",
    ),
    INVENTORY_VIEW_ROLES,
  ),
  sales: withRoleGate(
    lazyRouteComponent(() => import("../features/sales/sales-page"), "SalesPage"),
    SALES_VIEW_ROLES,
  ),
  iot: withRoleGate(
    lazyRouteComponent(() => import("../features/iot/iot-page"), "IotPage"),
    IOT_VIEW_ROLES,
  ),
  adminUsers: withRoleGate(
    lazyRouteComponent(
      () => import("../features/admin/admin-users-page"),
      "AdminUsersPage",
    ),
    ["SYSTEM_ADMIN"],
  ),
  publicTraceability: lazyRouteComponent(
    () => import("./public-traceability-route"),
    "PublicTraceabilityRoute",
  ),
} as const;
