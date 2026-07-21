import type { ApiClient } from "../../lib/api/client";
import type { AssistantGenerationProjection } from "./assistant-generation-projection";
import type {
  AssistantGenerationConnectionPhase,
  AssistantGenerationRecoveryNotice,
  runAssistantGeneration,
} from "./assistant-generation-runner";

export type AssistantGenerationControllerPhase =
  | "IDLE"
  | "SUBMITTING"
  | "SUBMIT_FAILED"
  | AssistantGenerationConnectionPhase;

export interface PendingAssistantSubmission {
  prompt: string;
  idempotencyKey: string;
}

export interface AssistantGenerationControllerState {
  projection: AssistantGenerationProjection | null;
  phase: AssistantGenerationControllerPhase;
  recovery: AssistantGenerationRecoveryNotice | null;
  syncErrorCode: string | null;
  submissionError: unknown;
  cancellationError: unknown;
  pendingPrompt: string | null;
  isSubmitting: boolean;
  isCancelling: boolean;
}

export interface UseAssistantGenerationOptions {
  api: ApiClient;
  conversationId: string;
  createIdempotencyKey?: () => string;
  runner?: typeof runAssistantGeneration;
  onHistoryChanged?: () => void;
}

export const INITIAL_ASSISTANT_GENERATION_STATE: AssistantGenerationControllerState = {
  projection: null,
  phase: "IDLE",
  recovery: null,
  syncErrorCode: null,
  submissionError: null,
  cancellationError: null,
  pendingPrompt: null,
  isSubmitting: false,
  isCancelling: false,
};
