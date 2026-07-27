import { useEffect, useRef, type RefObject } from "react";

import { isAssistantIdentifier } from "./assistant-identifiers";
import {
  createAssistantGenerationProjection,
  type AssistantGenerationProjection,
} from "./assistant-generation-projection";

interface AssistantGenerationResumeOptions {
  conversationId: string;
  initialGenerationId: string | null | undefined;
  launchRunner: (initial: AssistantGenerationProjection) => void;
  projectionRef: RefObject<AssistantGenerationProjection | null>;
}

export function useAssistantGenerationResume(options: AssistantGenerationResumeOptions): void {
  const { conversationId, initialGenerationId, launchRunner, projectionRef } = options;
  const launchedKeyRef = useRef<string | null>(null);

  useEffect(() => {
    if (!isAssistantIdentifier(initialGenerationId)) {
      launchedKeyRef.current = null;
      return;
    }
    const resumeKey = `${conversationId}:${initialGenerationId}`;
    let cancelled = false;
    queueMicrotask(() => {
      if (cancelled || launchedKeyRef.current === resumeKey) return;
      launchedKeyRef.current = resumeKey;
      if (projectionRef.current?.generationId !== initialGenerationId) {
        launchRunner(createAssistantGenerationProjection(initialGenerationId));
      }
    });
    return () => {
      cancelled = true;
    };
  }, [conversationId, initialGenerationId, launchRunner, projectionRef]);
}
