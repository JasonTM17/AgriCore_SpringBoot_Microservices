import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState } from "react";

import { ApiClientError } from "../../lib/api/errors";
import type { CropCycleResponse } from "../../lib/api/types";
import { hasAnyRole } from "../../lib/auth/roles";
import { useSession } from "../../lib/auth/session";
import { createWorkTask, listWorkTasks, type WorkTaskListParams } from "./work-task-api";
import type { WorkTaskCreateDraft } from "./work-task-create-form";
import { WorkTaskListPanel } from "./work-task-list-panel";
import { workTaskQueryKeys } from "./work-task-query-keys";

const PAGE_SIZE = 20;
const CREATE_ROLES = ["SYSTEM_ADMIN", "FARM_MANAGER", "AGRONOMIST"] as const;

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
  const [createFormResetKey, setCreateFormResetKey] = useState(0);
  const [createSuccess, setCreateSuccess] = useState<{ cycleId: string; message: string } | null>(null);
  const hasCreateSuccess = useRef(false);
  const params = useMemo<WorkTaskListParams>(() => ({
    cropCycleId: cycle.id,
    plotId: cycle.plotId,
    page,
    size: PAGE_SIZE,
  }), [cycle.id, cycle.plotId, page]);
  const tasksQuery = useQuery({
    queryKey: workTaskQueryKeys.list(subject, params),
    queryFn: async ({ signal }) => {
      try {
        return await listWorkTasks(api, params, signal);
      } catch (error) {
        if (isUnavailable(error) && hasCreateSuccess.current) {
          hasCreateSuccess.current = false;
          setCreateSuccess(null);
        }
        throw error;
      }
    },
    enabled: user !== null,
    staleTime: 0,
    retry: retryTaskFailure,
  });
  const createMutation = useMutation({
    mutationFn: (draft: WorkTaskCreateDraft) => createWorkTask(api, {
      ...draft,
      cropCycleId: cycle.id,
      plotId: cycle.plotId,
    }),
    onMutate: () => {
      hasCreateSuccess.current = false;
      setCreateSuccess(null);
    },
    onSuccess: async (createdTask) => {
      setPage(0);
      setCreateFormResetKey((current) => current + 1);
      hasCreateSuccess.current = true;
      setCreateSuccess({
        cycleId: cycle.id,
        message: `Đã tạo công việc ${createdTask.code}.`,
      });
      await queryClient.invalidateQueries({
        queryKey: workTaskQueryKeys.cycleLists(subject, cycle.id),
      });
    },
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

  const accessLost = isUnavailable(tasksQuery.error) || isUnavailable(createMutation.error);

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
      canCreate={hasAnyRole(user?.roles ?? [], CREATE_ROLES)}
      createError={createMutation.error}
      createFormResetKey={createFormResetKey}
      createSuccessMessage={!accessLost && createSuccess?.cycleId === cycle.id
        ? createSuccess.message
        : null}
      isCreating={createMutation.isPending}
      isCreateDisabled={accessLost || tasksQuery.isFetching || tasksQuery.error !== null}
      onCreate={(draft) => createMutation.mutate(draft)}
      onRecoverCreateError={() => {
        createMutation.reset();
        void tasksQuery.refetch();
      }}
      onRetry={() => void tasksQuery.refetch()}
      onPrevious={() => setPage((current) => Math.max(0, current - 1))}
      onNext={() => setPage((current) => current + 1)}
    />
  );
}
