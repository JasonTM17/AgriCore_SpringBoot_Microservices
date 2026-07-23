import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { useSession } from "../../lib/auth/session";
import { hasAnyRole } from "../../lib/auth/roles";
import {
  getHarvest,
  getHarvestCompletionEventStatus,
  getInventoryHarvestProjectionAcknowledgement,
  getTraceabilityHarvestProjectionAcknowledgement,
  republishHarvestCompletionEvent,
} from "./harvest-api";
import {
  isHarvestUnavailable,
  retryHarvestFailure,
} from "./harvest-error-policy";
import { harvestQueryKeys } from "./harvest-query-keys";
import { HARVEST_WORKFLOW_ROLES } from "./harvest-roles";
import {
  HarvestReceiptError,
  HarvestReceiptSkeleton,
} from "./harvest-receipt-load-state";
import {
  HarvestReceiptPanel,
  type ProjectionQueryState,
} from "./harvest-receipt-panel";
import type {
  HarvestCompletionEventStatusResponse,
  InventoryHarvestProjectionAcknowledgementResponse,
  TraceabilityHarvestProjectionAcknowledgementResponse,
} from "../../lib/api/types";

export function HarvestReceiptPage({ harvestId }: { harvestId: string }) {
  const { api, user } = useSession();
  const queryClient = useQueryClient();
  const subject = user?.id ?? "unauthenticated";
  const canOperate = hasAnyRole(user?.roles ?? [], HARVEST_WORKFLOW_ROLES);
  const detailKey = harvestQueryKeys.detail(subject, harvestId);
  const producerKey = harvestQueryKeys.producer(subject, harvestId);
  const detailQuery = useQuery({
    queryKey: detailKey,
    queryFn: ({ signal }) => getHarvest(api, harvestId, signal),
    enabled: user !== null && harvestId.length > 0,
    staleTime: 0,
    refetchOnMount: "always",
    retry: retryHarvestFailure,
  });
  const validatedHarvest = detailQuery.isFetchedAfterMount
    && !detailQuery.isFetching
    && detailQuery.error === null
    ? detailQuery.data
    : undefined;
  const producerEnabled = user !== null && validatedHarvest !== undefined;
  const producerQuery = useQuery({
    queryKey: producerKey,
    queryFn: ({ signal }) => getHarvestCompletionEventStatus(api, harvestId, signal),
    enabled: producerEnabled,
    staleTime: 0,
    retry: retryHarvestFailure,
    refetchInterval: (query) => {
      const state = query.state.data?.state;
      return state === "ENQUEUED" || state === "RETRYING" ? 2_000 : false;
    },
  });
  const validatedProducer = producerQuery.isFetchedAfterMount
    && !producerQuery.isFetching
    && producerQuery.error === null
    ? producerQuery.data
    : undefined;
  const eventId = validatedHarvest?.lastOutboxEventId ?? null;
  const acknowledgementsEnabled = canOperate
    && eventId !== null
    && validatedProducer?.state === "PUBLISHED";
  const inventoryQuery = useQuery({
    queryKey: harvestQueryKeys.inventory(subject, eventId ?? "unavailable"),
    queryFn: ({ signal }) => {
      if (!eventId || !validatedHarvest) {
        throw new Error("Cannot load inventory acknowledgement without harvest scope");
      }
      return getInventoryHarvestProjectionAcknowledgement(
        api,
        eventId,
        validatedHarvest.warehouseId,
        signal,
      );
    },
    enabled: acknowledgementsEnabled,
    staleTime: 0,
    retry: retryHarvestFailure,
    refetchInterval: (query) => query.state.data?.state === "NOT_ACKNOWLEDGED" ? 3_000 : false,
  });
  const traceabilityQuery = useQuery({
    queryKey: harvestQueryKeys.traceability(subject, eventId ?? "unavailable"),
    queryFn: ({ signal }) => {
      if (!eventId) throw new Error("Cannot load traceability acknowledgement without event identity");
      return getTraceabilityHarvestProjectionAcknowledgement(api, eventId, signal);
    },
    enabled: acknowledgementsEnabled,
    staleTime: 0,
    retry: retryHarvestFailure,
    refetchInterval: (query) => query.state.data?.state === "NOT_ACKNOWLEDGED" ? 3_000 : false,
  });
  const repairMutation = useMutation({
    mutationFn: () => republishHarvestCompletionEvent(api, harvestId),
    retry: false,
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: producerKey, exact: true });
    },
    onSuccess: async (updatedStatus: HarvestCompletionEventStatusResponse) => {
      await queryClient.cancelQueries({ queryKey: producerKey, exact: true });
      queryClient.setQueryData(producerKey, updatedStatus);
      if (eventId) {
        void queryClient.invalidateQueries({ queryKey: harvestQueryKeys.event(subject, eventId) });
      }
    },
  });

  const projectionState = <T,>(
    query: {
      data: T | undefined;
      error: Error | null;
      isPending: boolean;
      isFetching: boolean;
      refetch: () => Promise<unknown>;
    },
    enabled: boolean,
  ): ProjectionQueryState<T> => ({
    data: enabled ? query.data : undefined,
    error: enabled ? query.error : null,
    isPending: enabled && query.isPending,
    isFetching: enabled && query.isFetching,
    onRetry: () => void query.refetch(),
  });

  async function refreshAll() {
    repairMutation.reset();
    await queryClient.cancelQueries({ queryKey: producerKey, exact: true });
    if (eventId) {
      await queryClient.cancelQueries({ queryKey: harvestQueryKeys.event(subject, eventId) });
    }

    const detailResult = await detailQuery.refetch();
    if (!detailResult.isSuccess) return;

    const producerResult = await producerQuery.refetch();
    if (!producerResult.isSuccess || producerResult.data.state !== "PUBLISHED") return;
    if (!canOperate || !detailResult.data.lastOutboxEventId) return;

    await Promise.all([inventoryQuery.refetch(), traceabilityQuery.refetch()]);
  }

  const fatalError = detailQuery.error && (!detailQuery.data || isHarvestUnavailable(detailQuery.error))
    ? detailQuery.error
    : null;

  return (
    <div>
      {detailQuery.isPending ? <HarvestReceiptSkeleton /> : null}
      {!detailQuery.isPending && fatalError ? (
        <HarvestReceiptError error={fatalError} onRetry={() => void detailQuery.refetch()} />
      ) : null}
      {!detailQuery.isPending && !fatalError && detailQuery.data ? (
        <HarvestReceiptPanel
          harvest={detailQuery.data}
          producer={projectionState<HarvestCompletionEventStatusResponse>(producerQuery, producerEnabled)}
          inventory={projectionState<InventoryHarvestProjectionAcknowledgementResponse>(inventoryQuery, acknowledgementsEnabled)}
          traceability={projectionState<TraceabilityHarvestProjectionAcknowledgementResponse>(traceabilityQuery, acknowledgementsEnabled)}
          canReadAcknowledgements={canOperate}
          canRepair={canOperate}
          repairError={repairMutation.error}
          isRepairing={repairMutation.isPending}
          onRepair={() => repairMutation.mutate()}
          onRefreshAll={() => void refreshAll()}
        />
      ) : null}
    </div>
  );
}
