import { Navigate, Outlet, useRouterState } from "@tanstack/react-router";
import type { ReactNode } from "react";

import { AppShell } from "../components/layout/app-shell";
import { ForbiddenPage } from "../features/system/status-pages";
import { hasAnyRole, type NavItem } from "../lib/auth/roles";
import { useSession } from "../lib/auth/session";

function BootstrapScreen() {
  return (
    <div className="grid min-h-screen place-items-center bg-canvas text-sm text-muted">
      Đang khôi phục phiên làm việc...
    </div>
  );
}

export function AuthGate({ children }: { children: ReactNode }) {
  const { status } = useSession();
  const pathname = useRouterState({ select: (state) => state.location.pathname });

  if (status === "bootstrapping") {
    return <BootstrapScreen />;
  }
  if (status !== "authenticated") {
    return <Navigate to="/login" search={{ redirect: pathname }} replace />;
  }
  return children;
}

export function RoleGate({
  roles,
  children,
}: {
  roles: NavItem["roles"];
  children: ReactNode;
}) {
  const { user } = useSession();
  if (!hasAnyRole(user?.roles ?? [], roles)) {
    return <ForbiddenPage />;
  }
  return children;
}

export function AuthenticatedLayout() {
  return (
    <AuthGate>
      <AppShell>
        <Outlet />
      </AppShell>
    </AuthGate>
  );
}
