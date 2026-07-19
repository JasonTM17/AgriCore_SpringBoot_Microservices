import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";

import { ApiClientError } from "../../lib/api/errors";
import type { CropCycleResponse } from "../../lib/api/types";
import { useSession } from "../../lib/auth/session";
import { listWorkTasks, type WorkTaskListParams } from "./work-task-api";
import { WorkTaskListPanel } from "./work-task-list-panel";
import { workTaskQueryKeys } from "./work-task-query-keys";

const PAGE_SIZE = 20;

function isUnavailable(error: unknown): error is ApiClientError {
  return error instanceof ApiClientError && (error.status === 403 || error.status === 404);
}

function retryTaskFailure(failureCount: number, error: Error): boolean {
  if (failureCount >= 1) return false;
  if (!(error instanceof ApiClientError)) return true;
  return error.status === 408 || error.status === 429 || error.status >= 500;
}

export function WorkTaskWorkspace({ cycle }: { cycle: CropCycleResponse }) {
  const { api, user } = useSession();
  const queryClient = useQueryClient();
  const subject = user?.id ?? "unauthenticated";
  const [page, setPage] = useState(0);
  const params = useMemo<WorkTaskListParams>(() => ({
    cropCycleId: cycle.id,
    plotId: cycle.plotId,
    page,
    size: PAGE_SIZE,
  }), [cycle.id, cycle.plotId, page]);
  const tasksQuery = useQuery({
    queryKey: workTaskQueryKeys.list(subject, params),
    queryFn: ({ signal }) => listWorkTasks(api, params, signal),
    enabled: user !== null,
    staleTime: 0,
    retry: retryTaskFailure,
  });

  useEffect(() => {
    if (!tasksQuery.data) return;
    const lastAvailablePage = Math.max(0, tasksQuery.data.totalPages - 1);
    if (page <= lastAvailablePage) return;
    const recoveryTimer = window.setTimeout(() => {
      void queryClient.invalidateQueries({
        queryKey: workTaskQueryKeys.cycleLists(subject, cycle.id),
      });
      setPage(lastAvailablePage);
    }, 0);
    return () => window.clearTimeout(recoveryTimer);
  }, [cycle.id, page, queryClient, subject, tasksQuery.data]);

  const accessLost = isUnavailable(tasksQuery.error);

  useEffect(() => {
    if (!accessLost) return;
    queryClient.removeQueries({
      queryKey: workTaskQueryKeys.cycleLists(subject, cycle.id),
    });
  }, [accessLost, cycle.id, queryClient, subject]);

  return (
    <WorkTaskListPanel
      cycleCode={cycle.code}
      data={accessLost ? undefined : tasksQuery.data}
      error={tasksQuery.error}
      isPending={tasksQuery.isPending}
      isFetching={tasksQuery.isFetching}
      canGoPrevious={page > 0}
      onRetry={() => void tasksQuery.refetch()}
      onPrevious={() => setPage((current) => Math.max(0, current - 1))}
      onNext={() => setPage((current) => current + 1)}
    />
  );
}
