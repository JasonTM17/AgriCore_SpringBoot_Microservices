import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";

import type { CropCycleResponse } from "../../lib/api/types";
import { hasAnyRole } from "../../lib/auth/roles";
import { useSession } from "../../lib/auth/session";
import { useWorkTaskActions } from "./work-task-actions";
import { listWorkTasks, type WorkTaskListParams } from "./work-task-api";
import { isWorkTaskUnavailable, retryWorkTaskFailure } from "./work-task-error-policy";
import { WorkTaskListPanel } from "./work-task-list-panel";
import { workTaskQueryKeys } from "./work-task-query-keys";

const PAGE_SIZE = 20;
const WORK_MANAGER_ROLES = ["SYSTEM_ADMIN", "FARM_MANAGER", "AGRONOMIST"] as const;
const WORK_EXECUTION_ROLES = [...WORK_MANAGER_ROLES, "FIELD_WORKER"] as const;

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
  const actions = useWorkTaskActions({
    api,
    cycle,
    subject,
    onTaskCreated: () => setPage(0),
  });
  const tasksQuery = useQuery({
    queryKey: workTaskQueryKeys.list(subject, params),
    queryFn: async ({ signal }) => {
      try {
        return await listWorkTasks(api, params, signal);
      } catch (error) {
        if (isWorkTaskUnavailable(error)) actions.clearSuccessOnAccessLoss();
        throw error;
      }
    },
    enabled: user !== null,
    staleTime: 0,
    retry: retryWorkTaskFailure,
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

  const accessLost = isWorkTaskUnavailable(tasksQuery.error) || actions.unavailableError !== null;
  const loadError = tasksQuery.error
    ?? actions.blockingUnavailableError;

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
      error={loadError}
      isPending={tasksQuery.isPending}
      isFetching={tasksQuery.isFetching}
      canGoPrevious={page > 0}
      canCreate={hasAnyRole(user?.roles ?? [], WORK_MANAGER_ROLES)}
      createError={actions.create.error}
      createFormResetKey={actions.create.formResetKey}
      createSuccessMessage={accessLost ? null : actions.create.successMessage}
      isCreating={actions.create.isPending}
      isCreateDisabled={accessLost
        || tasksQuery.isFetching
        || tasksQuery.error !== null
        || actions.assignment.isPending
        || actions.start.isPending
        || actions.completion.isPending}
      assignment={{
        canAssign: hasAnyRole(user?.roles ?? [], WORK_MANAGER_ROLES),
        error: actions.assignment.error,
        taskId: actions.assignment.taskId,
        isPending: actions.assignment.isPending,
        isDisabled: accessLost
          || tasksQuery.isFetching
          || tasksQuery.error !== null
          || actions.create.isPending
          || actions.start.isPending
          || actions.completion.isPending,
        success: accessLost ? null : actions.assignment.success,
        onAssign: (taskId, assignedEmployeeId) => actions.assignment.mutate({ taskId, assignedEmployeeId }),
        onRecoverError: () => {
          actions.assignment.reset();
          void tasksQuery.refetch();
        },
      }}
      start={{
        canStart: hasAnyRole(user?.roles ?? [], WORK_EXECUTION_ROLES),
        error: actions.start.error,
        taskId: actions.start.taskId,
        isPending: actions.start.isPending,
        isDisabled: accessLost
          || tasksQuery.isFetching
          || tasksQuery.error !== null
          || actions.create.isPending
          || actions.assignment.isPending
          || actions.completion.isPending,
        success: accessLost ? null : actions.start.success,
        onStart: (taskId) => actions.start.mutate({ taskId }),
        onRecoverError: () => {
          actions.start.reset();
          void tasksQuery.refetch();
        },
      }}
      completion={{
        canComplete: hasAnyRole(user?.roles ?? [], WORK_EXECUTION_ROLES),
        error: actions.completion.error,
        taskId: actions.completion.taskId,
        isPending: actions.completion.isPending,
        isDisabled: accessLost
          || tasksQuery.isFetching
          || tasksQuery.error !== null
          || actions.create.isPending
          || actions.assignment.isPending
          || actions.start.isPending,
        success: accessLost ? null : actions.completion.success,
        onComplete: (taskId, draft) => actions.completion.mutate({ taskId, draft }),
        onRecoverError: () => {
          actions.completion.reset();
          void tasksQuery.refetch();
        },
      }}
      onCreate={(draft) => actions.create.mutate(draft)}
      onRecoverCreateError={() => {
        actions.create.reset();
        void tasksQuery.refetch();
      }}
      onRetry={() => {
        actions.resetUnavailableMutations();
        void tasksQuery.refetch();
      }}
      onPrevious={() => setPage((current) => Math.max(0, current - 1))}
      onNext={() => setPage((current) => current + 1)}
    />
  );
}
