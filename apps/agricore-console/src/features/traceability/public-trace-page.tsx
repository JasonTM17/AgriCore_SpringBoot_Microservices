import { useQuery } from "@tanstack/react-query";
import { useParams } from "@tanstack/react-router";
import { type FormEvent, useState } from "react";

import { Button } from "../../components/ui/button";
import { Input } from "../../components/ui/input";
import {
  ErrorBlock,
  LoadingBlock,
  OpsPage,
} from "../../components/ops/resource-state";
import { createDomainApi } from "../../lib/api/domain-api";
import { useSession } from "../../lib/auth/session";

export function PublicTracePage() {
  const params = useParams({ strict: false });
  const routeCode = typeof params.code === "string" ? params.code : "";
  const { api } = useSession();
  const domain = createDomainApi(api);
  const [code, setCode] = useState(routeCode);
  const [active, setActive] = useState(routeCode);

  const query = useQuery({
    queryKey: ["public-trace", active],
    queryFn: ({ signal }) => domain.getPublicTrace(active, signal),
    enabled: Boolean(active),
    retry: 1,
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (code.trim()) {
      setActive(code.trim());
    }
  }

  return (
    <main className="min-h-screen bg-canvas px-4 py-8">
      <OpsPage
        title="Truy xuất nguồn gốc công khai"
        description="Payload public QR — không yêu cầu đăng nhập, không hiển thị UUID nội bộ/PII."
      >
        <form onSubmit={onSubmit} className="flex flex-wrap items-end gap-3">
          <div className="min-w-[12rem] flex-1">
            <Input
              label="Mã truy xuất"
              name="code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
            />
          </div>
          <Button type="submit">Tra cứu</Button>
        </form>

        {query.isLoading ? <LoadingBlock /> : null}
        {query.isError ? (
          <ErrorBlock error={query.error} onRetry={() => void query.refetch()} />
        ) : null}
        {query.data ? (
          <article className="rounded-card border border-border bg-surface p-6 text-sm leading-6">
            <h2 className="text-xl font-bold">{query.data.productName}</h2>
            <p className="text-muted">{query.data.batchLabel ?? query.data.traceabilityCode}</p>
            <dl className="mt-4 grid gap-3 sm:grid-cols-2">
              <div>
                <dt className="text-xs uppercase text-muted">Nông trại</dt>
                <dd>{query.data.farmName}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase text-muted">Lô</dt>
                <dd>{query.data.plotCode}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase text-muted">Giống</dt>
                <dd>{query.data.varietyName ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase text-muted">Chất lượng</dt>
                <dd>{query.data.qualityGrade ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase text-muted">Ngày trồng</dt>
                <dd>{query.data.plantingDate ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase text-muted">Ngày thu hoạch</dt>
                <dd>{query.data.harvestDate ?? "—"}</dd>
              </div>
            </dl>
            {query.data.careSummary ? (
              <p className="mt-4 rounded-control bg-forest-50 p-3">{query.data.careSummary}</p>
            ) : null}
          </article>
        ) : null}
      </OpsPage>
    </main>
  );
}
