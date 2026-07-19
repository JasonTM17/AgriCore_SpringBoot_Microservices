import {
  Outlet,
  createRootRoute,
  createRoute,
  createRouter,
} from "@tanstack/react-router";

import {
  ForbiddenPage,
  NotFoundPage,
  PlaceholderModulePage,
} from "../features/system/status-pages";
import type { NavItem } from "../lib/auth/roles";
import { sanitizeInternalRedirect } from "../lib/auth/redirects";
import { AuthenticatedLayout, RoleGate } from "./auth-gates";
import { lazyRouteComponents } from "./lazy-route-components";
import { RouteErrorState, RouteLoadingState } from "./route-states";
import { SessionRouteBoundary } from "./session-route-boundary";

const rootRoute = createRootRoute({
  component: () => <Outlet />,
  notFoundComponent: NotFoundPage,
});

const sessionRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: "session",
  component: SessionRouteBoundary,
});

const loginRoute = createRoute({
  getParentRoute: () => sessionRoute,
  path: "/login",
  validateSearch: (search: Record<string, unknown>) => ({
    redirect: sanitizeInternalRedirect(search.redirect),
  }),
  component: lazyRouteComponents.login,
});

const authedLayoutRoute = createRoute({
  getParentRoute: () => sessionRoute,
  id: "authed",
  component: AuthenticatedLayout,
});

const dashboardRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/",
  component: lazyRouteComponents.dashboard,
});

function moduleRoute(
  path: string,
  title: string,
  description: string,
  roles: NavItem["roles"],
) {
  return createRoute({
    getParentRoute: () => authedLayoutRoute,
    path,
    component: () => (
      <RoleGate roles={roles}>
        <PlaceholderModulePage title={title} description={description} />
      </RoleGate>
    ),
  });
}

const farmsRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/farms",
  component: lazyRouteComponents.farms,
});
const cropsRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/crops",
  component: lazyRouteComponents.crops,
});
const cyclesRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/crop-cycles",
  component: lazyRouteComponents.cropCycles,
});
const cycleDetailRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/crop-cycles/$cycleId",
  component: lazyRouteComponents.cropCycleDetail,
});
const harvestsRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/harvests",
  component: lazyRouteComponents.harvests,
});
const harvestReceiptRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/harvests/$harvestId",
  component: lazyRouteComponents.harvestReceipt,
});
const inventoryRoute = moduleRoute(
  "/inventory",
  "Kho vận",
  "Tồn kho, giữ hàng và xác nhận reservation.",
  ["SYSTEM_ADMIN", "WAREHOUSE_MANAGER", "SALES_STAFF", "AUDITOR"],
);
const salesRoute = moduleRoute(
  "/sales",
  "Bán hàng",
  "Đơn bán và trạng thái saga inventory.",
  ["SYSTEM_ADMIN", "SALES_STAFF", "WAREHOUSE_MANAGER", "AUDITOR"],
);
const iotRoute = moduleRoute(
  "/iot",
  "IoT",
  "Đăng ký thiết bị và nạp reading chẩn đoán.",
  ["SYSTEM_ADMIN", "FARM_MANAGER", "AGRONOMIST", "AUDITOR"],
);
const adminRoute = moduleRoute(
  "/admin/users",
  "Quản trị người dùng",
  "Danh sách người dùng và cập nhật vai trò (SYSTEM_ADMIN).",
  ["SYSTEM_ADMIN"],
);
const assistantRoute = moduleRoute(
  "/assistant",
  "Trợ lý vận hành",
  "Chat assistant sẽ được nối sau khi assistant-service sẵn sàng.",
  "all",
);

const publicTraceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/public/traceability/$code",
  component: lazyRouteComponents.publicTraceability,
});

const forbiddenRoute = createRoute({
  getParentRoute: () => sessionRoute,
  path: "/forbidden",
  component: ForbiddenPage,
});

const routeTree = rootRoute.addChildren([
  publicTraceRoute,
  sessionRoute.addChildren([
    loginRoute,
    forbiddenRoute,
    authedLayoutRoute.addChildren([
      dashboardRoute,
      farmsRoute,
      cropsRoute,
      cyclesRoute,
      cycleDetailRoute,
      harvestsRoute,
      harvestReceiptRoute,
      inventoryRoute,
      salesRoute,
      iotRoute,
      adminRoute,
      assistantRoute,
    ]),
  ]),
]);

export const router = createRouter({
  routeTree,
  defaultPreload: "intent",
  defaultPendingComponent: RouteLoadingState,
  defaultPendingMs: 150,
  defaultPendingMinMs: 250,
  defaultErrorComponent: RouteErrorState,
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
