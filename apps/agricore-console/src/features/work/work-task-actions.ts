import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useCallback, useRef, useState } from "react";

import type { ApiClient } from "../../lib/api/client";
import type { CompleteTaskRequest, CropCycleResponse } from "../../lib/api/types";
import {
  assignWorkTask,
  completeWorkTask,
  createWorkTask,
  startWorkTask,
} from "./work-task-api";
import type { WorkTaskCreateDraft } from "./work-task-create-form";
import { isWorkTaskUnavailable } from "./work-task-error-policy";
import { workTaskQueryKeys } from "./work-task-query-keys";

type ActionSuccess = {
  type: "create" | "assign" | "start" | "complete";
  resourceId: string;
  message: string;
};

interface WorkTaskActionsOptions {
  api: ApiClient;
  cycle: Pick<CropCycleResponse, "id" | "plotId">;
  subject: string;
  onTaskCreated: () => void;
}

export function useWorkTaskActions({ api, cycle, subject, onTaskCreated }: WorkTaskActionsOptions) {
  const queryClient = useQueryClient();
  const [createFormResetKey, setCreateFormResetKey] = useState(0);
  const [success, setSuccess] = useState<ActionSuccess | null>(null);
  const hasSuccess = useRef(false);

  const clearSuccessOnAccessLoss = useCallback(() => {
    if (!hasSuccess.current) return;
    hasSuccess.current = false;
    setSuccess(null);
  }, []);

  function clearSuccess() {
    hasSuccess.current = false;
    setSuccess(null);
  }

  function recordSuccess(next: ActionSuccess) {
    hasSuccess.current = true;
    setSuccess(next);
  }

  function invalidateCycleTasks() {
    return queryClient.invalidateQueries({
      queryKey: workTaskQueryKeys.cycleLists(subject, cycle.id),
    });
  }

  const createMutation = useMutation({
    mutationFn: (draft: WorkTaskCreateDraft) => createWorkTask(api, {
      ...draft,
      cropCycleId: cycle.id,
      plotId: cycle.plotId,
    }),
    onMutate: clearSuccess,
    onSuccess: async (createdTask) => {
      onTaskCreated();
      setCreateFormResetKey((current) => current + 1);
      recordSuccess({
        type: "create",
        resourceId: cycle.id,
        message: `Đã tạo công việc ${createdTask.code}.`,
      });
      await invalidateCycleTasks();
    },
  });
  const assignMutation = useMutation({
    mutationFn: ({ taskId, assignedEmployeeId }: { taskId: string; assignedEmployeeId: string }) =>
      assignWorkTask(api, taskId, { assignedEmployeeId }),
    onMutate: clearSuccess,
    onSuccess: async (assignedTask) => {
      recordSuccess({
        type: "assign",
        resourceId: assignedTask.id,
        message: `Đã phân công công việc ${assignedTask.code}.`,
      });
      await invalidateCycleTasks();
    },
  });
  const startMutation = useMutation({
    mutationFn: ({ taskId }: { taskId: string }) => startWorkTask(api, taskId),
    onMutate: clearSuccess,
    onSuccess: async (startedTask) => {
      recordSuccess({
        type: "start",
        resourceId: startedTask.id,
        message: `Đã bắt đầu công việc ${startedTask.code}.`,
      });
      await invalidateCycleTasks();
    },
  });
  const completeMutation = useMutation({
    mutationFn: ({ taskId, draft }: { taskId: string; draft: CompleteTaskRequest }) =>
      completeWorkTask(api, taskId, draft),
    onMutate: clearSuccess,
    onSuccess: async (completedTask) => {
      recordSuccess({
        type: "complete",
        resourceId: completedTask.id,
        message: `Đã hoàn tất công việc ${completedTask.code}.`,
      });
      await invalidateCycleTasks();
    },
  });
  const unavailableError = [
    createMutation.error,
    assignMutation.error,
    startMutation.error,
    completeMutation.error,
  ]
    .find(isWorkTaskUnavailable) ?? null;
  const blockingUnavailableError = [
    assignMutation.error,
    startMutation.error,
    completeMutation.error,
  ]
    .find(isWorkTaskUnavailable) ?? null;

  return {
    clearSuccessOnAccessLoss,
    unavailableError,
    blockingUnavailableError,
    resetUnavailableMutations: () => {
      createMutation.reset();
      assignMutation.reset();
      startMutation.reset();
      completeMutation.reset();
    },
    create: {
      error: createMutation.error,
      formResetKey: createFormResetKey,
      isPending: createMutation.isPending,
      mutate: createMutation.mutate,
      reset: createMutation.reset,
      successMessage: success?.type === "create" && success.resourceId === cycle.id ? success.message : null,
    },
    assignment: {
      error: assignMutation.error,
      taskId: assignMutation.variables?.taskId ?? null,
      isPending: assignMutation.isPending,
      mutate: assignMutation.mutate,
      reset: assignMutation.reset,
      success: success?.type === "assign"
        ? { taskId: success.resourceId, message: success.message }
        : null,
    },
    start: {
      error: startMutation.error,
      taskId: startMutation.variables?.taskId ?? null,
      isPending: startMutation.isPending,
      mutate: startMutation.mutate,
      reset: startMutation.reset,
      success: success?.type === "start"
        ? { taskId: success.resourceId, message: success.message }
        : null,
    },
    completion: {
      error: completeMutation.error,
      taskId: completeMutation.variables?.taskId ?? null,
      isPending: completeMutation.isPending,
      mutate: completeMutation.mutate,
      reset: completeMutation.reset,
      success: success?.type === "complete"
        ? { taskId: success.resourceId, message: success.message }
        : null,
    },
  };
}
