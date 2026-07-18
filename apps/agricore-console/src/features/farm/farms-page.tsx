import { useQuery } from "@tanstack/react-query";
import { useState } from "react";

import {
  ApiGapNotice,
  DataTable,
  ErrorBlock,
  LoadingBlock,
  OpsPage,
} from "../../components/ops/resource-state";
import { createDomainApi } from "../../lib/api/domain-api";
import { LIVE_API_CAPABILITIES } from "../../lib/api/domain-types";
import { useSession } from "../../lib/auth/session";
import { hasAnyRole } from "../../lib/auth/roles";

export function FarmsPage() {
  const { api, user } = useSession();
  const domain = createDomainApi(api);
  const [selectedFarmId, setSelectedFarmId] = useState<string | null>(null);
  const canMutate = hasAnyRole(user?.roles ?? [], ["SYSTEM_ADMIN", "FARM_MANAGER"]);

  const farmsQuery = useQuery({
    queryKey: ["farms", "list"],
    queryFn: ({ signal }) => domain.listFarms(0, 50, signal),
  });

  const plotsQuery = useQuery({
    queryKey: ["farms", selectedFarmId, "plots"],
    queryFn: ({ signal }) => domain.listPlots(selectedFarmId!, 0, 50, signal),
    enabled: Boolean(selectedFarmId),
  });

  return (
    <OpsPage
      title="Nông trại & lô canh tác"
      description="Danh sách và chi tiết lô từ farm-service. Không vẽ bản đồ/geometry vì API không cung cấp."
    >
      {!LIVE_API_CAPABILITIES.dashboardAggregate ? (
        <ApiGapNotice
          capability="dashboardAggregate"
          detail="Không có endpoint KPI tổng hợp — trang này chỉ dùng GET /api/v1/farms và plots."
        />
      ) : null}

      {farmsQuery.isLoading ? <LoadingBlock /> : null}
      {farmsQuery.isError ? (
        <ErrorBlock error={farmsQuery.error} onRetry={() => void farmsQuery.refetch()} />
      ) : null}
      {farmsQuery.data ? (
        <DataTable
          headers={["Mã", "Tên", "Tỉnh", "Trạng thái", "Diện tích (ha)", "Chi tiết"]}
          empty="Chưa có nông trại trong hệ thống."
          rows={farmsQuery.data.content.map((farm) => [
            <code key="c" className="font-mono text-xs">
              {farm.code}
            </code>,
            farm.name,
            farm.province,
            farm.status,
            String(farm.totalAreaHa),
            <button
              key="d"
              type="button"
              className="text-sm font-semibold text-forest-700 underline"
              onClick={() => setSelectedFarmId(farm.id)}
            >
              Xem lô
            </button>,
          ])}
        />
      ) : null}

      {!canMutate ? (
        <ApiGapNotice
          capability="createFarm"
          detail="Vai trò hiện tại không có quyền tạo/sửa nông trại (cần SYSTEM_ADMIN hoặc FARM_MANAGER)."
        />
      ) : (
        <ApiGapNotice
          capability="createFarmForm"
          detail="Form tạo nông trại có thể bật (POST /api/v1/farms) — UI tạo đầy đủ sẽ theo phase mở rộng; quyền đã xác nhận ở backend."
        />
      )}

      {selectedFarmId ? (
        <section className="space-y-3">
          <h2 className="text-lg font-semibold">Lô của nông trại đã chọn</h2>
          {plotsQuery.isLoading ? <LoadingBlock label="Đang tải lô..." /> : null}
          {plotsQuery.isError ? (
            <ErrorBlock error={plotsQuery.error} onRetry={() => void plotsQuery.refetch()} />
          ) : null}
          {plotsQuery.data ? (
            <DataTable
              headers={["Mã lô", "Tên", "Diện tích", "Đất", "Trạng thái"]}
              empty="Nông trại chưa có lô."
              rows={plotsQuery.data.content.map((plot) => [
                <code key="c" className="font-mono text-xs">
                  {plot.code}
                </code>,
                plot.name,
                String(plot.areaHa),
                plot.soilType ?? "—",
                plot.status,
              ])}
            />
          ) : null}
        </section>
      ) : null}
    </OpsPage>
  );
}
