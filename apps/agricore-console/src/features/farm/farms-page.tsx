import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";

import type { FarmResponse } from "../../lib/api/types";
import { useSession } from "../../lib/auth/session";
import { listFarmPlots, listFarms, type FarmListParams, type PlotListParams } from "./farm-api";
import { FarmListPanel } from "./farm-list-panel";
import { farmQueryKeys } from "./farm-query-keys";
import { useFarmScope } from "./farm-scope-context";
import { PlotListPanel } from "./plot-list-panel";

const PAGE_SIZE = 20;

export function FarmsPage() {
  const { api, user } = useSession();
  const subject = user?.id ?? "unauthenticated";
  const { activeFarm, selectFarm } = useFarmScope();
  const [farmPage, setFarmPage] = useState(0);
  const [plotPage, setPlotPage] = useState(0);

  const farmParams = useMemo<FarmListParams>(
    () => ({ page: farmPage, size: PAGE_SIZE, sort: "code,asc" }),
    [farmPage],
  );
  const plotParams = useMemo<PlotListParams>(
    () => ({ page: plotPage, size: PAGE_SIZE }),
    [plotPage],
  );

  const farmsQuery = useQuery({
    queryKey: farmQueryKeys.list(subject, farmParams),
    queryFn: ({ signal }) => listFarms(api, farmParams, signal),
    enabled: user !== null,
  });
  const activeFarmId = activeFarm?.id ?? null;
  const plotsQuery = useQuery({
    queryKey: farmQueryKeys.plots(subject, activeFarmId ?? "none", plotParams),
    queryFn: ({ signal }) => {
      if (!activeFarmId) {
        throw new Error("Cannot load plots without an active farm");
      }
      return listFarmPlots(api, activeFarmId, plotParams, signal);
    },
    enabled: user !== null && activeFarmId !== null,
  });

  function handleSelectFarm(farm: FarmResponse) {
    selectFarm(farm);
    setPlotPage(0);
  }

  return (
    <div className="space-y-6">
      <header>
        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-forest-700">
          Farm workspace
        </p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-ink">
          Nông trại & lô canh tác
        </h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">
          Dữ liệu lấy trực tiếp từ farm-service. Lựa chọn nông trại chỉ là ngữ cảnh điều hướng;
          backend vẫn kiểm tra membership cho từng request.
        </p>
      </header>

      <div className="grid gap-6 xl:grid-cols-[minmax(20rem,0.85fr)_minmax(0,1.4fr)]">
        <FarmListPanel
          data={farmsQuery.data}
          error={farmsQuery.error}
          isPending={farmsQuery.isPending}
          isFetching={farmsQuery.isFetching}
          activeFarmId={activeFarm?.id ?? null}
          onSelect={handleSelectFarm}
          onRetry={() => void farmsQuery.refetch()}
          onPrevious={() => setFarmPage((page) => Math.max(0, page - 1))}
          onNext={() => setFarmPage((page) => page + 1)}
        />
        <PlotListPanel
          farm={activeFarm}
          data={plotsQuery.data}
          error={plotsQuery.error}
          isPending={activeFarm !== null && plotsQuery.isPending}
          isFetching={activeFarm !== null && plotsQuery.isFetching}
          waitingForFarm={farmsQuery.isPending}
          onRetry={() => void plotsQuery.refetch()}
          onPrevious={() => setPlotPage((page) => Math.max(0, page - 1))}
          onNext={() => setPlotPage((page) => page + 1)}
        />
      </div>
    </div>
  );
}
