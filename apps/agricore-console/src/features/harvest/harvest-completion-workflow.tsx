import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { useMemo, useState } from "react";

import type { FarmResponse } from "../../lib/api/types";
import { useSession } from "../../lib/auth/session";
import {
  listCropCycles,
  type CropCycleListParams,
} from "../crop-cycle/crop-cycle-api";
import { cropCycleQueryKeys } from "../crop-cycle/crop-cycle-query-keys";
import { getFarm, getPlot } from "../farm/farm-api";
import { farmQueryKeys } from "../farm/farm-query-keys";
import { completeHarvest } from "./harvest-api";
import { HarvestCompletionForm } from "./harvest-completion-form";
import {
  HarvestScopeError,
  HarvestScopeStatus,
} from "./harvest-completion-scope-state";
import { isHarvestUnavailable, retryHarvestFailure } from "./harvest-error-policy";
import { harvestQueryKeys } from "./harvest-query-keys";

const PAGE_SIZE = 20;
const CYCLE_SCOPE_MISMATCH = new Error("Crop-cycle response crossed the validated farm scope");

export function HarvestCompletionWorkflow({
  farm,
  onResetScope,
}: {
  farm: FarmResponse;
  onResetScope: () => void;
}) {
  const { api, user } = useSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const subject = user?.id ?? "unauthenticated";
  const [page, setPage] = useState(0);
  const [selectedCycleId, setSelectedCycleId] = useState("");
  const [isRecovering, setIsRecovering] = useState(false);

  const farmValidationQuery = useQuery({
    queryKey: farmQueryKeys.detail(subject, farm.id),
    queryFn: ({ signal }) => getFarm(api, farm.id, signal),
    enabled: user !== null,
    staleTime: 0,
    refetchOnMount: "always",
    retry: retryHarvestFailure,
  });
  const farmMismatch = farmValidationQuery.data !== undefined
    && farmValidationQuery.data.id !== farm.id;
  const validatedFarm = farmValidationQuery.isFetchedAfterMount
    && !farmValidationQuery.isFetching
    && farmValidationQuery.error === null
    && !farmMismatch
    ? (farmValidationQuery.data ?? null)
    : null;
  const cycleParams = useMemo<CropCycleListParams | null>(
    () => validatedFarm ? { farmId: validatedFarm.id, page, size: PAGE_SIZE } : null,
    [page, validatedFarm],
  );
  const cyclesQuery = useQuery({
    queryKey: cropCycleQueryKeys.list(
      subject,
      cycleParams ?? { farmId: "unvalidated", page, size: PAGE_SIZE },
    ),
    queryFn: ({ signal }) => {
      if (!cycleParams) throw new Error("Cannot load crop cycles before validating farm scope");
      return listCropCycles(api, cycleParams, signal);
    },
    enabled: cycleParams !== null,
    staleTime: 0,
    retry: retryHarvestFailure,
  });
  const cycleScopeMismatch = cyclesQuery.data?.content.some(
    (cycle) => cycle.farmId !== validatedFarm?.id,
  ) ?? false;
  const validatedCycles = cyclesQuery.isFetchedAfterMount
    && !cyclesQuery.isFetching
    && cyclesQuery.error === null
    && !cycleScopeMismatch
    ? cyclesQuery.data
    : undefined;
  const selectedCycle = validatedCycles?.content.find(
    (cycle) => cycle.id === selectedCycleId,
  ) ?? null;
  const plotId = selectedCycle?.plotId ?? "unselected";
  const plotQuery = useQuery({
    queryKey: farmQueryKeys.plotDetail(subject, plotId),
    queryFn: ({ signal }) => {
      if (!selectedCycle) throw new Error("Cannot load a plot before selecting a cycle");
      return getPlot(api, selectedCycle.plotId, signal);
    },
    enabled: selectedCycle !== null,
    staleTime: 0,
    retry: retryHarvestFailure,
  });
  const plotMismatch = plotQuery.data !== undefined && selectedCycle !== null
    && (plotQuery.data.id !== selectedCycle.plotId || plotQuery.data.farmId !== validatedFarm?.id);
  const validatedPlot = plotQuery.isFetchedAfterMount
    && !plotQuery.isFetching
    && plotQuery.error === null
    && !plotMismatch
    ? (plotQuery.data ?? null)
    : null;
  const completionMutation = useMutation({
    mutationFn: (request: Parameters<typeof completeHarvest>[1]) => completeHarvest(api, request),
    retry: false,
    onSuccess: async (harvest) => {
      const detailKey = harvestQueryKeys.detail(subject, harvest.id);
      await queryClient.cancelQueries({ queryKey: detailKey, exact: true });
      queryClient.setQueryData(detailKey, harvest);
      await navigate({ to: "/harvests/$harvestId", params: { harvestId: harvest.id } });
    },
  });

  async function recoverContext() {
    setIsRecovering(true);
    completionMutation.reset();
    try {
      const farmResult = await farmValidationQuery.refetch();
      if (!farmResult.isSuccess) return;
      const cyclesResult = await cyclesQuery.refetch();
      if (!cyclesResult.isSuccess || !selectedCycle) return;
      await plotQuery.refetch();
    } finally {
      setIsRecovering(false);
    }
  }

  function changePage(nextPage: number) {
    setSelectedCycleId("");
    completionMutation.reset();
    setPage(Math.max(0, nextPage));
  }

  async function resetDeniedScope() {
    onResetScope();
    await queryClient.invalidateQueries({ queryKey: farmQueryKeys.subject(subject) });
  }

  if (farmValidationQuery.error || farmMismatch) {
    const accessDenied = isHarvestUnavailable(farmValidationQuery.error);
    return (
      <HarvestScopeError
        error={farmValidationQuery.error}
        mismatch={farmMismatch}
        actionLabel={accessDenied ? "Tải lại phạm vi" : "Xác minh lại"}
        onRetry={accessDenied
          ? () => void resetDeniedScope()
          : () => void farmValidationQuery.refetch()}
      />
    );
  }
  if (!validatedFarm) {
    return <HarvestScopeStatus message="Đang xác minh quyền trên nông trại…" />;
  }

  return (
    <HarvestCompletionForm
      farm={validatedFarm}
      cycles={validatedCycles}
      cyclesError={cycleScopeMismatch ? CYCLE_SCOPE_MISMATCH : cyclesQuery.error}
      isCyclesPending={cyclesQuery.isPending || !cyclesQuery.isFetchedAfterMount}
      isCyclesFetching={cyclesQuery.isFetching}
      selectedCycle={selectedCycle}
      selectedCycleId={selectedCycleId}
      plot={validatedPlot}
      plotError={plotQuery.error}
      plotMismatch={plotMismatch}
      isPlotPending={selectedCycle !== null && (plotQuery.isPending || !plotQuery.isFetchedAfterMount)}
      isSubmitting={completionMutation.isPending}
      isContextRefreshing={isRecovering}
      mutationError={completionMutation.error}
      onSelectCycle={setSelectedCycleId}
      onRetryCycles={() => void cyclesQuery.refetch()}
      onPreviousCycles={() => changePage(page - 1)}
      onNextCycles={() => changePage(page + 1)}
      onSubmit={(request) => completionMutation.mutate(request)}
      onEdit={() => completionMutation.reset()}
      onRecoverContext={() => void recoverContext()}
    />
  );
}
