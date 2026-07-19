import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";

import { getFarm, listFarms, type FarmListParams } from "../farm/farm-api";
import { farmQueryKeys } from "../farm/farm-query-keys";
import { useFarmScope } from "../farm/farm-scope-context";
import { ApiClientError } from "../../lib/api/errors";
import type { FarmPageResponse, FarmResponse } from "../../lib/api/types";
import { useSession } from "../../lib/auth/session";
import { listCropCycles, type CropCycleListParams } from "./crop-cycle-api";
import { CropCycleListPanel } from "./crop-cycle-list-panel";
import { cropCycleQueryKeys } from "./crop-cycle-query-keys";
import { FarmScopeSelector } from "./farm-scope-selector";

const PAGE_SIZE = 20;
const DEFAULT_FARM_PARAMS = {
  page: 0,
  size: PAGE_SIZE,
  sort: "code,asc",
} as const satisfies FarmListParams;

function isFarmAccessError(error: unknown): error is ApiClientError {
  return error instanceof ApiClientError && (error.status === 403 || error.status === 404);
}

function retryTransientFailure(failureCount: number, error: Error): boolean {
  return !isFarmAccessError(error) && failureCount < 1;
}

export function CropCyclesPage() {
  const { api, user } = useSession();
  const queryClient = useQueryClient();
  const subject = user?.id ?? "unauthenticated";
  const { activeFarm, selectFarm } = useFarmScope();
  const [page, setPage] = useState(0);
  const [farmPage, setFarmPage] = useState(0);
  const farmParams = useMemo<FarmListParams>(
    () => ({ ...DEFAULT_FARM_PARAMS, page: farmPage }),
    [farmPage],
  );
  const farmsQuery = useQuery({
    queryKey: farmQueryKeys.list(subject, farmParams),
    queryFn: ({ signal }) => listFarms(api, farmParams, signal),
    enabled: user !== null,
  });
  useEffect(() => {
    if (!farmsQuery.data) return;
    const lastAvailablePage = Math.max(0, farmsQuery.data.totalPages - 1);
    if (farmPage <= lastAvailablePage) return;

    const recoveryTimer = window.setTimeout(() => {
      void queryClient.invalidateQueries({ queryKey: farmQueryKeys.lists(subject) });
      setFarmPage(lastAvailablePage);
    }, 0);
    return () => window.clearTimeout(recoveryTimer);
  }, [farmPage, farmsQuery.data, queryClient, subject]);
  const activeFarmId = activeFarm?.id ?? null;
  const farmValidationQuery = useQuery({
    queryKey: farmQueryKeys.detail(subject, activeFarmId ?? "none"),
    queryFn: ({ signal }) => {
      if (!activeFarmId) throw new Error("Cannot validate an empty farm scope");
      return getFarm(api, activeFarmId, signal);
    },
    enabled: user !== null && activeFarmId !== null,
    staleTime: 0,
    refetchOnMount: "always",
    retry: retryTransientFailure,
  });
  const validationError = farmValidationQuery.error;
  const validatedFarm = farmValidationQuery.isFetchedAfterMount && !validationError
    ? (farmValidationQuery.data ?? null)
    : null;
  const cycleParams = useMemo<CropCycleListParams | null>(
    () => validatedFarm ? { farmId: validatedFarm.id, page, size: PAGE_SIZE } : null,
    [page, validatedFarm],
  );
  const cyclesQuery = useQuery({
    queryKey: cropCycleQueryKeys.list(
      subject,
      cycleParams ?? { farmId: "none", page, size: PAGE_SIZE },
    ),
    queryFn: ({ signal }) => {
      if (!cycleParams) throw new Error("Cannot load crop cycles without an active farm");
      return listCropCycles(api, cycleParams, signal);
    },
    enabled: user !== null && cycleParams !== null,
    retry: retryTransientFailure,
  });
  const cycleAccessDenied = isFarmAccessError(cyclesQuery.error);
  const scopeError = validationError ?? (cycleAccessDenied ? cyclesQuery.error : null);
  const accessDenied = isFarmAccessError(scopeError);

  function handleSelectFarm(farm: FarmResponse) {
    selectFarm(farm);
    setPage(0);
  }

  async function resetDeniedScope() {
    const deniedFarmId = activeFarmId;
    selectFarm(null);
    setFarmPage(0);
    setPage(0);
    await queryClient.invalidateQueries({ queryKey: farmQueryKeys.subject(subject) });

    const refreshedScope = queryClient.getQueryState<FarmPageResponse>(
      farmQueryKeys.list(subject, DEFAULT_FARM_PARAMS),
    );
    const refreshedFarmId = refreshedScope?.status === "success"
      ? refreshedScope.data?.content[0]?.id
      : null;
    if (deniedFarmId && refreshedFarmId === deniedFarmId) {
      await queryClient.invalidateQueries({
        queryKey: cropCycleQueryKeys.farmLists(subject, deniedFarmId),
      });
    }
  }

  return (
    <div className="space-y-6">
      <header>
        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-forest-700">Crop cycles</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-ink">Mùa vụ & giai đoạn</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">
          Danh sách lấy trực tiếp từ crop-cycle-service và luôn gửi phạm vi farm đã chọn.
          Trạng thái backend là nguồn sự thật; màn hình không suy diễn transition.
        </p>
      </header>

      <FarmScopeSelector
        data={farmsQuery.data}
        error={farmsQuery.error}
        isPending={farmsQuery.isPending}
        isFetching={farmsQuery.isFetching}
        activeFarm={validatedFarm ?? activeFarm}
        validationError={scopeError}
        isValidating={farmValidationQuery.isFetching}
        excludedFarmId={accessDenied ? activeFarmId : null}
        onSelect={handleSelectFarm}
        onRetry={() => void farmsQuery.refetch()}
        onRetryValidation={() => void farmValidationQuery.refetch()}
        onResetScope={() => void resetDeniedScope()}
        onPreviousPage={() => setFarmPage((current) => Math.max(0, current - 1))}
        onNextPage={() => setFarmPage((current) => current + 1)}
      />

      <CropCycleListPanel
        farm={cycleAccessDenied ? null : validatedFarm}
        data={cyclesQuery.data}
        error={cycleAccessDenied ? null : cyclesQuery.error}
        isPending={
          farmsQuery.isPending
          || (activeFarm !== null && !farmValidationQuery.isFetchedAfterMount)
          || (validatedFarm !== null && cyclesQuery.isPending)
        }
        isFetching={validatedFarm !== null && !cycleAccessDenied && cyclesQuery.isFetching}
        onRetry={() => void cyclesQuery.refetch()}
        onPrevious={() => setPage((current) => Math.max(0, current - 1))}
        onNext={() => setPage((current) => current + 1)}
      />
    </div>
  );
}
