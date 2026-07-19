import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { Button } from "../../components/ui/button";
import { ApiClientError } from "../../lib/api/errors";
import type { ChangeStageRequest, CropCycleResponse } from "../../lib/api/types";
import { hasAnyRole } from "../../lib/auth/roles";
import { useSession } from "../../lib/auth/session";
import { WorkTaskWorkspace } from "../work/work-task-workspace";
import { changeCropCycleStage, getCropCycle } from "./crop-cycle-api";
import { cropCycleQueryKeys } from "./crop-cycle-query-keys";
import { allowedNextStages } from "./crop-cycle-stage-policy";
import { CropCycleDetailPanel } from "./crop-cycle-detail-panel";

const STAGE_MUTATION_ROLES = ["SYSTEM_ADMIN", "FARM_MANAGER", "AGRONOMIST"] as const;

function isUnavailable(error: unknown): error is ApiClientError {
  return error instanceof ApiClientError && (error.status === 403 || error.status === 404);
}

function requiresAuthoritativeReload(error: unknown): boolean {
  return error instanceof ApiClientError && (error.status === 409 || error.status === 503);
}

function retryDetailFailure(failureCount: number, error: Error): boolean {
  if (isUnavailable(error) || (error instanceof ApiClientError && error.status === 422)) {
    return false;
  }
  return failureCount < 1;
}

function DetailSkeleton() {
  return (
    <div className="space-y-6" role="status" aria-label="Đang tải chi tiết mùa vụ">
      <div className="h-5 w-48 animate-pulse rounded bg-forest-50" />
      <div className="h-80 animate-pulse rounded-card bg-forest-50" />
    </div>
  );
}

function DetailError({ error, onRetry }: { error: Error; onRetry: () => void }) {
  const accessChanged = isUnavailable(error);
  const supportCode = error instanceof ApiClientError ? error.code : null;
  return (
    <section className="rounded-card border border-danger/30 bg-red-50 p-6" role="alert">
      <h1 className="text-2xl font-bold text-ink">Chi tiết mùa vụ</h1>
      <p className="mt-3 text-sm leading-6 text-ink">
        {accessChanged
          ? "Mùa vụ không còn khả dụng hoặc bạn không còn quyền truy cập."
          : "Không thể tải chi tiết mùa vụ. Dịch vụ có thể đang gián đoạn."}
      </p>
      {supportCode ? <p className="mt-2 text-xs text-muted">Mã hỗ trợ: {supportCode}</p> : null}
      <Button className="mt-4" variant="secondary" onClick={onRetry}>Tải lại trạng thái</Button>
    </section>
  );
}

export function CropCycleDetailPage({ cycleId }: { cycleId: string }) {
  const { api, user } = useSession();
  const queryClient = useQueryClient();
  const [formResetKey, setFormResetKey] = useState(0);
  const subject = user?.id ?? "unauthenticated";
  const detailKey = cropCycleQueryKeys.detail(subject, cycleId);
  const detailQuery = useQuery({
    queryKey: detailKey,
    queryFn: ({ signal }) => getCropCycle(api, cycleId, signal),
    enabled: user !== null && cycleId.length > 0,
    staleTime: 0,
    refetchOnMount: "always",
    retry: retryDetailFailure,
  });
  const stageMutation = useMutation({
    mutationFn: ({ stage, notes }: ChangeStageRequest) =>
      changeCropCycleStage(api, cycleId, { stage, notes: notes ?? null }),
    retry: false,
    onMutate: async () => {
      // Do not let a refetch that started before the mutation overwrite the
      // committed response returned by the stage endpoint.
      await queryClient.cancelQueries({ queryKey: detailKey, exact: true });
    },
    onSuccess: async (updatedCycle: CropCycleResponse) => {
      // A reconnect can start a refetch while the POST is in flight. Cancel it
      // after the server commits and before publishing the authoritative body.
      await queryClient.cancelQueries({ queryKey: detailKey, exact: true });
      queryClient.setQueryData(detailKey, updatedCycle);
      setFormResetKey((key) => key + 1);
      void queryClient.invalidateQueries({ queryKey: cropCycleQueryKeys.lists(subject) });
    },
  });
  async function reloadDetail() {
    const result = await detailQuery.refetch();
    if (result.isSuccess) {
      stageMutation.reset();
    }
  }

  const { data, error, isFetching, isPending } = detailQuery;
  if (isPending) return <DetailSkeleton />;
  if (error && (!data || isUnavailable(error))) {
    return <DetailError error={error} onRetry={() => void reloadDetail()} />;
  }
  if (!data) {
    return (
      <DetailError
        error={new Error("Crop cycle response was empty")}
        onRetry={() => void reloadDetail()}
      />
    );
  }

  const cycle = data;
  const accessRevoked = isUnavailable(stageMutation.error);
  const canMutate = hasAnyRole(user?.roles ?? [], STAGE_MUTATION_ROLES) && !accessRevoked;
  const mutationNeedsReload = requiresAuthoritativeReload(stageMutation.error);
  return (
    <div className="space-y-6">
      <CropCycleDetailPanel
        cycle={cycle}
        canMutate={canMutate}
        allowedStages={allowedNextStages(cycle.stage)}
        isMutating={stageMutation.isPending}
        isReloading={isFetching}
        isInteractionLocked={isFetching || error !== null || mutationNeedsReload}
        actionError={stageMutation.error}
        refreshError={error}
        formResetKey={formResetKey}
        onChangeStage={(stage, notes) => stageMutation.mutate({ stage, notes })}
        onReload={() => void reloadDetail()}
      />
      <WorkTaskWorkspace key={`${cycle.id}:${cycle.plotId}`} cycle={cycle} />
    </div>
  );
}
