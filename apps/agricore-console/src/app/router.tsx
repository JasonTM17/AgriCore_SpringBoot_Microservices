import {
  Outlet,
  createRootRoute,
  createRoute,
  createRouter,
} from "@tanstack/react-router";

import { ForbiddenPage, NotFoundPage } from "../features/system/status-pages";
import { sanitizeInternalRedirect } from "../lib/auth/redirects";
import { AuthenticatedLayout } from "./auth-gates";
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
const inventoryRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/inventory",
  component: lazyRouteComponents.inventory,
});
const salesRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/sales",
  component: lazyRouteComponents.sales,
});
const iotRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/iot",
  component: lazyRouteComponents.iot,
});
const adminRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/admin/users",
  component: lazyRouteComponents.adminUsers,
});
const assistantRoute = createRoute({
  getParentRoute: () => authedLayoutRoute,
  path: "/assistant",
  component: lazyRouteComponents.assistant,
});

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
