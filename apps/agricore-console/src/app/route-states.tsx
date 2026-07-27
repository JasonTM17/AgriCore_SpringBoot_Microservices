import type { ErrorComponentProps } from "@tanstack/react-router";

import { Button } from "../components/ui/button";

export function RouteLoadingState() {
  return (
    <section
      className="grid min-h-[24rem] content-center gap-5 bg-canvas px-5 py-10"
      role="status"
      aria-label="Đang tải nội dung"
      aria-busy="true"
      aria-live="polite"
    >
      <span className="sr-only">Đang tải nội dung trang…</span>
      <div className="mx-auto w-full max-w-4xl space-y-4" aria-hidden="true">
        <div className="h-8 w-2/5 motion-safe:animate-pulse rounded bg-forest-100" />
        <div className="h-24 motion-safe:animate-pulse rounded-card bg-forest-50" />
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="h-40 motion-safe:animate-pulse rounded-card bg-forest-50" />
          <div className="h-40 motion-safe:animate-pulse rounded-card bg-forest-50" />
        </div>
      </div>
    </section>
  );
}

export function RouteErrorState({ reset }: ErrorComponentProps) {
  return (
    <section
      className="grid min-h-[24rem] place-items-center bg-canvas px-5 py-10 text-center"
      role="alert"
      aria-labelledby="route-error-heading"
    >
      <div className="max-w-lg rounded-card border border-danger/30 bg-red-50 p-6">
        <h1 id="route-error-heading" className="text-xl font-semibold text-danger">
          Không thể mở trang
        </h1>
        <p className="mt-2 text-sm leading-6 text-muted">
          Nội dung chưa tải được. Kiểm tra kết nối rồi thử lại; thông tin kỹ thuật không được
          hiển thị tại đây.
        </p>
        <div className="mt-5 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <Button onClick={reset}>Thử tải lại</Button>
          <a
            href={window.location.href}
            className="inline-flex h-10 items-center justify-center rounded-control border border-border bg-surface px-4 text-sm font-semibold text-ink hover:bg-forest-50"
          >
            Tải lại toàn bộ trang
          </a>
        </div>
      </div>
    </section>
  );
}
