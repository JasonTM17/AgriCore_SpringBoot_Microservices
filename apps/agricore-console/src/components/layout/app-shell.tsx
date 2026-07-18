import { Link, useRouterState } from "@tanstack/react-router";
import { useMemo, useState } from "react";

import { Button } from "../ui/button";
import { visibleNavItems } from "../../lib/auth/roles";
import { useSession } from "../../lib/auth/session";

export function AppShell({ children }: { children: React.ReactNode }) {
  const { user, logout } = useSession();
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const [mobileOpen, setMobileOpen] = useState(false);

  const items = useMemo(() => visibleNavItems(user?.roles ?? []), [user?.roles]);

  return (
    <div className="min-h-screen bg-canvas text-ink md:grid md:grid-cols-[264px_1fr]">
      <aside
        id="mobile-nav"
        className={`fixed inset-y-0 left-0 z-40 w-[264px] max-w-[85vw] transform bg-forest-900 text-white transition-transform md:static md:translate-x-0 ${
          mobileOpen ? "translate-x-0" : "-translate-x-full"
        }`}
        aria-label="Điều hướng chính"
      >
        <div className="flex h-[72px] items-center gap-3 border-b border-white/10 px-5">
          <span className="grid size-10 place-items-center rounded-control bg-white/10 text-base font-bold">
            A
          </span>
          <div>
            <p className="text-sm font-bold tracking-tight">AgriCore</p>
            <p className="text-xs text-white/70">Operations Console</p>
          </div>
        </div>
        <nav className="space-y-1 p-3">
          {items.map((item) => {
            const active = pathname === item.to || (item.to !== "/" && pathname.startsWith(item.to));
            return (
              <Link
                key={item.id}
                to={item.to}
                onClick={() => setMobileOpen(false)}
                className={`flex h-11 items-center rounded-control px-3 text-sm font-medium transition-colors ${
                  active ? "bg-forest-700 text-white" : "text-white/85 hover:bg-white/10"
                }`}
                aria-current={active ? "page" : undefined}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
      </aside>

      {mobileOpen ? (
        <button
          type="button"
          className="fixed inset-0 z-30 bg-ink/40 md:hidden"
          aria-label="Đóng menu"
          onClick={() => setMobileOpen(false)}
        />
      ) : null}

      <div className="min-w-0">
        <header className="sticky top-0 z-20 flex h-[72px] items-center justify-between border-b border-border bg-surface px-4 md:px-8">
          <div className="flex items-center gap-3">
            <Button
              variant="ghost"
              className="md:hidden"
              aria-expanded={mobileOpen}
              aria-controls="mobile-nav"
              onClick={() => setMobileOpen((open) => !open)}
            >
              Menu
            </Button>
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-muted">
                Nền tảng vận hành
              </p>
              <p className="text-sm font-semibold text-ink">AgriCore Console</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <div className="hidden text-right sm:block">
              <p className="text-sm font-semibold">{user?.fullName}</p>
              <p className="text-xs text-muted">{user?.roles.join(", ")}</p>
            </div>
            <Button variant="secondary" onClick={() => void logout()}>
              Đăng xuất
            </Button>
          </div>
        </header>
        <main className="px-4 py-6 md:px-8 md:py-8">{children}</main>
      </div>
    </div>
  );
}
