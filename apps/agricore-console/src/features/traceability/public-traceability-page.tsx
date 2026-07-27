import { useQuery } from "@tanstack/react-query";
import { useState } from "react";

import { ApiClient } from "../../lib/api/client";
import { getPublicTraceability } from "./public-traceability-api";
import { PublicTraceabilityPanel } from "./public-traceability-panel";
import {
  normalizeTraceabilityCode,
  publicTraceabilityQueryKeys,
  retryPublicTraceability,
} from "./public-traceability-query";
import { PublicTraceabilitySearch } from "./public-traceability-search";
import {
  PublicTraceabilityError,
  PublicTraceabilityInvalid,
  PublicTraceabilitySkeleton,
} from "./public-traceability-state";

export function PublicTraceabilityPage({ code }: { code: string }) {
  const [api] = useState(
    () => new ApiClient({
      getAccessToken: () => null,
      setAccessToken: () => undefined,
    }),
  );
  const normalizedCode = normalizeTraceabilityCode(code);
  const query = useQuery({
    queryKey: publicTraceabilityQueryKeys.detail(normalizedCode ?? "invalid"),
    queryFn: ({ signal }) => {
      if (!normalizedCode) throw new Error("Cannot query an invalid traceability code");
      return getPublicTraceability(api, normalizedCode, signal);
    },
    enabled: normalizedCode !== null,
    staleTime: 60_000,
    retry: retryPublicTraceability,
  });

  return (
    <main className="min-h-screen bg-canvas">
      <header className="bg-forest-900 px-4 py-7 text-white sm:py-10">
        <div className="mx-auto max-w-3xl">
          <div className="mb-6 flex items-center gap-3">
            <span className="grid size-10 place-items-center rounded-full bg-white/10 text-sm font-bold">A</span>
            <div>
              <p className="font-bold tracking-tight">AgriCore</p>
              <p className="text-xs text-white/70">Truy xuất nguồn gốc công khai</p>
            </div>
          </div>
          <PublicTraceabilitySearch key={code} initialCode={code} />
        </div>
      </header>

      <div className="mx-auto max-w-3xl px-4 py-6 sm:py-10">
        {!normalizedCode ? <PublicTraceabilityInvalid /> : null}
        {normalizedCode && query.isPending ? <PublicTraceabilitySkeleton /> : null}
        {normalizedCode && !query.isPending && query.error && !query.data ? (
          <PublicTraceabilityError
            error={query.error}
            isRetrying={query.isFetching}
            onRetry={() => void query.refetch()}
          />
        ) : null}
        {normalizedCode && query.data ? <PublicTraceabilityPanel data={query.data} /> : null}
      </div>
    </main>
  );
}
