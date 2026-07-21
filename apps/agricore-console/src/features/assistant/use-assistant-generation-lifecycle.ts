import {
  useCallback,
  useEffect,
  useRef,
  type Dispatch,
  type SetStateAction,
} from "react";

import type { ApiClient } from "../../lib/api/client";
import type { AssistantGenerationControllerState } from "./assistant-generation-controller-types";
import { INITIAL_ASSISTANT_GENERATION_STATE } from "./assistant-generation-controller-types";
import type { AssistantGenerationProjection } from "./assistant-generation-projection";
import { runAssistantGeneration } from "./assistant-generation-runner";

interface AssistantGenerationLifecycleOptions {
  api: ApiClient;
  conversationId: string;
  runner?: typeof runAssistantGeneration;
  onGenerationChanged?: (generationId: string | null) => void;
  onHistoryChanged?: () => void;
}

export function useAssistantGenerationLifecycle(
  options: AssistantGenerationLifecycleOptions,
  setState: Dispatch<SetStateAction<AssistantGenerationControllerState>>,
) {
  const {
    api,
    conversationId,
    onGenerationChanged,
    onHistoryChanged,
    runner: runnerOverride,
  } = options;
  const mountedRef = useRef(true);
  const versionRef = useRef(0);
  const runnerAbortRef = useRef<AbortController | null>(null);
  const runnerActiveRef = useRef(false);
  const projectionRef = useRef<AssistantGenerationProjection | null>(null);
  const runner = runnerOverride ?? runAssistantGeneration;

  const updateForVersion = useCallback((version: number, update: (
    current: AssistantGenerationControllerState,
  ) => AssistantGenerationControllerState) => {
    if (mountedRef.current && versionRef.current === version) setState(update);
  }, [setState]);

  const launchRunner = useCallback((initial: AssistantGenerationProjection) => {
    runnerAbortRef.current?.abort();
    const controller = new AbortController();
    runnerAbortRef.current = controller;
    runnerActiveRef.current = true;
    projectionRef.current = initial;
    const version = ++versionRef.current;
    setState((current) => ({
      ...current,
      projection: initial,
      phase: "RECONCILING",
      recovery: null,
      syncErrorCode: null,
      submissionError: null,
      pendingPrompt: null,
      isSubmitting: false,
      isCancelling: false,
    }));

    void runner(api, {
      conversationId,
      generationId: initial.generationId,
      initialProjection: initial,
      signal: controller.signal,
      onProjection: (projection) => {
        projectionRef.current = projection;
        updateForVersion(version, (current) => ({ ...current, projection }));
      },
      onPhase: (phase) => {
        updateForVersion(version, (current) => ({ ...current, phase }));
      },
      onRecovery: (recovery) => {
        updateForVersion(version, (current) => ({ ...current, recovery }));
      },
    }).then((result) => {
      if (!mountedRef.current || versionRef.current !== version) return;
      runnerActiveRef.current = false;
      projectionRef.current = result.projection;
      setState((current) => ({
        ...current,
        projection: result.projection,
        phase: result.kind === "failed"
          ? "FAILED"
          : result.kind === "terminal" ? "TERMINAL" : "DETACHED",
        syncErrorCode: result.kind === "failed"
          ? (result.errorCode ?? "ASSISTANT_SYNC_FAILED")
          : null,
      }));
      if (result.kind === "terminal") {
        onGenerationChanged?.(null);
        onHistoryChanged?.();
      }
    }).catch(() => {
      updateForVersion(version, (current) => ({
        ...current,
        phase: "FAILED",
        syncErrorCode: "ASSISTANT_RUNNER_FAILED",
      }));
      if (versionRef.current === version) runnerActiveRef.current = false;
    });
  }, [
    api,
    conversationId,
    onGenerationChanged,
    onHistoryChanged,
    runner,
    setState,
    updateForVersion,
  ]);

  useEffect(() => {
    mountedRef.current = true;
    versionRef.current += 1;
    runnerAbortRef.current?.abort();
    runnerAbortRef.current = null;
    runnerActiveRef.current = false;
    projectionRef.current = null;
    setState(INITIAL_ASSISTANT_GENERATION_STATE);
    return () => {
      mountedRef.current = false;
      versionRef.current += 1;
      runnerAbortRef.current?.abort();
    };
  }, [conversationId, setState]);

  return {
    launchRunner,
    mountedRef,
    projectionRef,
    runnerActiveRef,
    updateForVersion,
    versionRef,
  };
}
