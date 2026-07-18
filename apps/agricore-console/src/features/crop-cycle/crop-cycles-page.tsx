import { useQuery } from "@tanstack/react-query";
import { useState } from "react";

import {
  DataTable,
  ErrorBlock,
  LoadingBlock,
  OpsPage,
} from "../../components/ops/resource-state";
import { createDomainApi } from "../../lib/api/domain-api";
import { useSession } from "../../lib/auth/session";

export function CropCyclesPage() {
  const { api } = useSession();
  const domain = createDomainApi(api);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const listQuery = useQuery({
    queryKey: ["crop-cycles"],
    queryFn: ({ signal }) => domain.listCropCycles(0, 50, signal),
  });

  const tasksQuery = useQuery({
    queryKey: ["work-tasks"],
    queryFn: ({ signal }) => domain.listWorkTasks(0, 50, signal),
  });

  const detailQuery = useQuery({
    queryKey: ["crop-cycles", selectedId],
    queryFn: ({ signal }) => domain.getCropCycle(selectedId!, signal),
    enabled: Boolean(selectedId),
  });

  return (
    <OpsPage
      title="Mùa vụ & công việc"
      description="Vòng đời mùa vụ và nhiệm vụ đồng ruộng từ crop-cycle-service / work-service. Stage change chỉ khi backend cho phép."
    >
      {listQuery.isLoading ? <LoadingBlock /> : null}
      {listQuery.isError ? (
        <ErrorBlock error={listQuery.error} onRetry={() => void listQuery.refetch()} />
      ) : null}
      {listQuery.data ? (
        <DataTable
          headers={["Mã", "Stage", "Status", "Farm", "Plot", "Chi tiết"]}
          empty="Chưa có mùa vụ."
          rows={listQuery.data.content.map((cycle) => [
            <code key="c" className="font-mono text-xs">
              {cycle.code}
            </code>,
            cycle.stage,
            cycle.status,
            <span key="f" className="font-mono text-xs">
              {cycle.farmId.slice(0, 8)}…
            </span>,
            <span key="p" className="font-mono text-xs">
              {cycle.plotId.slice(0, 8)}…
            </span>,
            <button
              key="d"
              type="button"
              className="text-sm font-semibold text-forest-700 underline"
              onClick={() => setSelectedId(cycle.id)}
            >
              Xem
            </button>,
          ])}
        />
      ) : null}

      {detailQuery.data ? (
        <article className="rounded-card border border-border bg-surface p-5 text-sm">
          <h2 className="text-lg font-semibold">{detailQuery.data.code}</h2>
          <dl className="mt-3 grid gap-2 sm:grid-cols-2">
            <div>
              <dt className="text-xs uppercase text-muted">Stage</dt>
              <dd className="font-medium">{detailQuery.data.stage}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase text-muted">Status</dt>
              <dd className="font-medium">{detailQuery.data.status}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase text-muted">Version</dt>
              <dd className="font-mono">{detailQuery.data.version}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase text-muted">Notes</dt>
              <dd>{detailQuery.data.notes ?? "—"}</dd>
            </div>
          </dl>
        </article>
      ) : null}

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Công việc (work-tasks)</h2>
        {tasksQuery.isLoading ? <LoadingBlock label="Đang tải công việc..." /> : null}
        {tasksQuery.isError ? (
          <ErrorBlock error={tasksQuery.error} onRetry={() => void tasksQuery.refetch()} />
        ) : null}
        {tasksQuery.data ? (
          <DataTable
            headers={["Mã", "Loại", "Tiêu đề", "Ưu tiên", "Status"]}
            empty="Chưa có công việc."
            rows={tasksQuery.data.content.map((task) => [
              <code key="c" className="font-mono text-xs">
                {task.code}
              </code>,
              task.taskType,
              task.title,
              task.priority,
              task.status,
            ])}
          />
        ) : null}
      </section>
    </OpsPage>
  );
}
