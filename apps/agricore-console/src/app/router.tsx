import {
  Outlet,
  createRootRoute,
  createRoute,
  createRouter,
} from "@tanstack/react-router";
import type { ReactNode } from "react";

import { AdminUsersPage } from "../features/admin/admin-users-page";
import { AssistantPage } from "../features/assistant/assistant-page";
import { LoginPage } from "../features/auth/login-page";
import { CropCyclesPage } from "../features/crop-cycle/crop-cycles-page";
import { DashboardPage } from "../features/dashboard/dashboard-page";
import { FarmsPage } from "../features/farm/farms-page";
import { HarvestPage } from "../features/harvest/harvest-page";
import { InventoryPage } from "../features/inventory/inventory-page";
import { IotPage } from "../features/iot/iot-page";
import { SalesPage } from "../features/sales/sales-page";
import { ForbiddenPage, NotFoundPage } from "../features/system/status-pages";
import { PublicTracePage } from "../features/traceability/public-trace-page";
import type { NavItem } from "../lib/auth/roles";
import { sanitizeInternalRedirect } from "../lib/auth/redirects";
import { AuthenticatedLayout, RoleGate } from "./auth-gates";

const rootRoute = createRootRoute({
  component: () => <Outlet />,
  notFoundComponent: NotFoundPage,
});

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/login",
  validateSearch: (search: Record<string, unknown>) => ({
    redirect: sanitizeInternalRedirect(search.redirect),
  }),
  component: LoginPage,
});

const authedLayoutRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: "authed",
  component: AuthenticatedLayout,
});

const dashboardRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/",
  component: DashboardPage,
});

function gated(roles: NavItem["roles"], page: ReactNode) {
  return () => <RoleGate roles={roles}>{page}</RoleGate>;
}

const farmsRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/farms",
  component: gated(
    ["SYSTEM_ADMIN", "FARM_MANAGER", "AGRONOMIST", "FIELD_WORKER", "AUDITOR"],
    <FarmsPage />,
  ),
});
const cyclesRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/crop-cycles",
  component: gated(
    ["SYSTEM_ADMIN", "FARM_MANAGER", "AGRONOMIST", "FIELD_WORKER", "AUDITOR"],
    <CropCyclesPage />,
  ),
});
const harvestsRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/harvests",
  component: gated(
    [
      "SYSTEM_ADMIN",
      "FARM_MANAGER",
      "AGRONOMIST",
      "FIELD_WORKER",
      "WAREHOUSE_MANAGER",
      "AUDITOR",
    ],
    <HarvestPage />,
  ),
});
const inventoryRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/inventory",
  component: gated(
    ["SYSTEM_ADMIN", "WAREHOUSE_MANAGER", "SALES_STAFF", "AUDITOR"],
    <InventoryPage />,
  ),
});
const salesRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/sales",
  component: gated(
    ["SYSTEM_ADMIN", "SALES_STAFF", "WAREHOUSE_MANAGER", "AUDITOR"],
    <SalesPage />,
  ),
});
const iotRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/iot",
  component: gated(
    ["SYSTEM_ADMIN", "FARM_MANAGER", "AGRONOMIST", "AUDITOR"],
    <IotPage />,
  ),
});
const adminRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/admin/users",
  component: gated(["SYSTEM_ADMIN"], <AdminUsersPage />),
});
const assistantRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/assistant",
  component: gated("all", <AssistantPage />),
});

const publicTraceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/public/traceability/$code",
  component: PublicTracePage,
});

const forbiddenRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/forbidden",
  component: ForbiddenPage,
});

const routeTree = rootRoute.addChildren([
  loginRoute,
  publicTraceRoute,
  forbiddenRoute,
  authedLayoutRoute.addChildren([
    dashboardRoute,
    farmsRoute,
    cyclesRoute,
    harvestsRoute,
    inventoryRoute,
    salesRoute,
    iotRoute,
    adminRoute,
    assistantRoute,
  ]),
]);

export const router = createRouter({
  routeTree,
  defaultPreload: "intent",
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
