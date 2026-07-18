import type { ReactNode } from "react";

import { EmptyState } from "../ui/empty-state";
import { ApiClientError } from "../../lib/api/errors";

export function LoadingBlock({ label = "Đang tải dữ liệu..." }: { label?: string }) {
  return (
    <div
      className="rounded-card border border-border bg-surface p-8 text-sm text-muted"
      role="status"
      aria-live="polite"
    >
      {label}
    </div>
  );
}

export function ErrorBlock({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message =
    error instanceof ApiClientError
      ? `${error.code}: ${error.message}`
      : error instanceof Error
        ? error.message
        : "Không thể tải dữ liệu.";
  return (
    <EmptyState
      title="Không tải được dữ liệu"
      description={message}
      action={
        onRetry ? (
          <button
            type="button"
            className="inline-flex h-10 items-center rounded-control bg-forest-700 px-4 text-sm font-semibold text-white"
            onClick={onRetry}
          >
            Thử lại
          </button>
        ) : undefined
      }
    />
  );
}

export function ApiGapNotice({ capability, detail }: { capability: string; detail: string }) {
  return (
    <div
      className="rounded-control border border-harvest-100 bg-harvest-100/40 px-4 py-3 text-sm text-ink"
      role="note"
    >
      <p className="font-semibold text-forest-900">API chưa có: {capability}</p>
      <p className="mt-1 text-muted">{detail}</p>
    </div>
  );
}

export function OpsPage({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <div className="animate-fade-in-up space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight text-ink">{title}</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">{description}</p>
      </div>
      {children}
    </div>
  );
}

export function DataTable({
  headers,
  rows,
  empty,
}: {
  headers: string[];
  rows: ReactNode[][];
  empty: string;
}) {
  if (rows.length === 0) {
    return <EmptyState title="Không có dữ liệu" description={empty} />;
  }
  return (
    <div className="overflow-x-auto rounded-card border border-border bg-surface">
      <table className="min-w-full text-left text-sm">
        <thead className="border-b border-border bg-forest-50 text-xs uppercase tracking-wide text-muted">
          <tr>
            {headers.map((h) => (
              <th key={h} className="px-4 py-3 font-semibold">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, idx) => (
            <tr key={idx} className="border-b border-border/70 last:border-0">
              {row.map((cell, cidx) => (
                <td key={cidx} className="px-4 py-3 align-top text-ink">
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
