import { Outlet } from "@tanstack/react-router";

import { SessionProvider } from "../lib/auth/session";

export function SessionRouteBoundary() {
  return (
    <SessionProvider>
      <Outlet />
    </SessionProvider>
  );
}
