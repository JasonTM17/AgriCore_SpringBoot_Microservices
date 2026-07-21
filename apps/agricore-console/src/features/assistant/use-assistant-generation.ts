import { useCallback, useEffect, useRef, useState } from "react";

import { cancelAssistantGeneration, submitAssistantGeneration } from "./assistant-api";
import {
  AssistantGenerationCommandError,
  createAssistantIdempotencyKey,
  normalizeAssistantPrompt,
  validateSubmittedGeneration,
} from "./assistant-generation-command";
import { createAssistantGenerationProjection, isTerminalAssistantStatus } from "./assistant-generation-projection";
import {
  INITIAL_ASSISTANT_GENERATION_STATE,
  type PendingAssistantSubmission,
  type UseAssistantGenerationOptions,
} from "./assistant-generation-controller-types";
import { useAssistantGenerationLifecycle } from "./use-assistant-generation-lifecycle";

export function useAssistantGeneration(options: UseAssistantGenerationOptions) {
  const {
    api,
    conversationId,
    createIdempotencyKey: idempotencyKeyFactory,
    onHistoryChanged,
    runner,
  } = options;
  const [state, setState] = useState(INITIAL_ASSISTANT_GENERATION_STATE);
  const commandAbortRef = useRef<AbortController | null>(null);
  const pendingRef = useRef<PendingAssistantSubmission | null>(null);
  const submittingRef = useRef(false);
  const cancellingRef = useRef(false);
  const {
    launchRunner,
    mountedRef,
    projectionRef,
    runnerActiveRef,
    updateForVersion,
    versionRef,
  } = useAssistantGenerationLifecycle({
    api,
    conversationId,
    ...(onHistoryChanged ? { onHistoryChanged } : {}),
    ...(runner ? { runner } : {}),
  }, setState);

  const submitPending = useCallback(async (
    pending: PendingAssistantSubmission,
  ): Promise<boolean> => {
    if (submittingRef.current) return false;
    submittingRef.current = true;
    const version = versionRef.current;
    const controller = new AbortController();
    commandAbortRef.current = controller;
    updateForVersion(version, (current) => ({
      ...current,
      phase: "SUBMITTING",
      submissionError: null,
      isSubmitting: true,
    }));
    try {
      const response = await submitAssistantGeneration(
        api,
        conversationId,
        { prompt: pending.prompt },
        pending.idempotencyKey,
        controller.signal,
      );
      if (!mountedRef.current || versionRef.current !== version) return false;
      const generation = validateSubmittedGeneration(response, conversationId);
      pendingRef.current = null;
      onHistoryChanged?.();
      launchRunner(createAssistantGenerationProjection(generation.id));
      return true;
    } catch (submissionError) {
      updateForVersion(version, (current) => ({
        ...current,
        phase: "SUBMIT_FAILED",
        submissionError,
      }));
      return false;
    } finally {
      if (commandAbortRef.current === controller) {
        commandAbortRef.current = null;
        submittingRef.current = false;
        updateForVersion(version, (current) => ({ ...current, isSubmitting: false }));
      }
    }
  }, [api, conversationId, launchRunner, mountedRef, onHistoryChanged, updateForVersion, versionRef]);

  const send = useCallback(async (value: string): Promise<boolean> => {
    try {
      if (submittingRef.current) return false;
      if (projectionRef.current && !isTerminalAssistantStatus(projectionRef.current.status)) {
        throw new AssistantGenerationCommandError(
          "GENERATION_ALREADY_ACTIVE",
          "Another assistant generation is still active",
        );
      }
      const prompt = normalizeAssistantPrompt(value);
      const pending = {
        prompt,
        idempotencyKey: createAssistantIdempotencyKey(idempotencyKeyFactory),
      };
      pendingRef.current = pending;
      setState((current) => ({ ...current, pendingPrompt: prompt }));
      return await submitPending(pending);
    } catch (submissionError) {
      const generationIsActive = submissionError instanceof AssistantGenerationCommandError
        && submissionError.code === "GENERATION_ALREADY_ACTIVE";
      setState((current) => ({
        ...current,
        phase: generationIsActive ? current.phase : "SUBMIT_FAILED",
        submissionError,
      }));
      return false;
    }
  }, [idempotencyKeyFactory, projectionRef, submitPending]);

  const retrySubmission = useCallback(() => {
    const pending = pendingRef.current;
    return pending ? submitPending(pending) : Promise.resolve(false);
  }, [submitPending]);

  const retryConnection = useCallback(() => {
    const projection = projectionRef.current;
    if (!projection || isTerminalAssistantStatus(projection.status)) return false;
    launchRunner(projection);
    return true;
  }, [launchRunner, projectionRef]);

  const cancel = useCallback(async (): Promise<boolean> => {
    const projection = projectionRef.current;
    if (!projection || isTerminalAssistantStatus(projection.status) || cancellingRef.current) {
      return false;
    }
    cancellingRef.current = true;
    const version = versionRef.current;
    const controller = new AbortController();
    commandAbortRef.current = controller;
    updateForVersion(version, (current) => ({
      ...current,
      cancellationError: null,
      isCancelling: true,
    }));
    try {
      const response = await cancelAssistantGeneration(
        api,
        conversationId,
        projection.generationId,
        controller.signal,
      );
      if (!mountedRef.current || versionRef.current !== version) return false;
      validateSubmittedGeneration(response, conversationId, projection.generationId);
      onHistoryChanged?.();
      if (!runnerActiveRef.current) launchRunner(projection);
      return true;
    } catch (cancellationError) {
      updateForVersion(version, (current) => ({ ...current, cancellationError }));
      return false;
    } finally {
      if (commandAbortRef.current === controller) {
        commandAbortRef.current = null;
        cancellingRef.current = false;
        updateForVersion(version, (current) => ({ ...current, isCancelling: false }));
      }
    }
  }, [
    api,
    conversationId,
    launchRunner,
    mountedRef,
    onHistoryChanged,
    projectionRef,
    runnerActiveRef,
    updateForVersion,
    versionRef,
  ]);

  useEffect(() => {
    commandAbortRef.current?.abort();
    commandAbortRef.current = null;
    pendingRef.current = null;
    submittingRef.current = false;
    cancellingRef.current = false;
    return () => {
      commandAbortRef.current?.abort();
    };
  }, [conversationId]);

  return { ...state, send, retrySubmission, retryConnection, cancel };
}
