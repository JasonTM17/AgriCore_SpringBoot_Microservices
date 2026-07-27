import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { AssistantGenerationStatus } from "./assistant-generation-status";
import { createAssistantGenerationProjection } from "./assistant-generation-projection";
import type { AssistantGenerationControllerState } from "./assistant-generation-controller-types";

const noop = vi.fn();

function state(overrides: Partial<AssistantGenerationControllerState> = {}) {
  return {
    projection: null,
    phase: "IDLE",
    recovery: null,
    syncErrorCode: null,
    submissionError: null,
    cancellationError: null,
    pendingPrompt: null,
    isSubmitting: false,
    isCancelling: false,
    ...overrides,
  } satisfies AssistantGenerationControllerState;
}

describe("AssistantGenerationStatus", () => {
  it("shows the limited outcome and a safe refusal message", () => {
    const projection = {
      ...createAssistantGenerationProjection("20000000-0000-0000-0000-000000000001"),
      status: "FAILED" as const,
      errorCode: "ASSISTANT_REQUEST_BUDGET_EXCEEDED",
    };
    render(<AssistantGenerationStatus
      state={state({ phase: "FAILED", projection })}
      onCancel={noop}
      onRetryConnection={noop}
      onRetrySubmission={noop}
    />);

    expect(screen.getByText("Đã chạm hạn mức")).toBeInTheDocument();
    expect(screen.getByText(/Bạn đã dùng hết hạn mức chatbot/)).toBeInTheDocument();
  });

  it("announces the active request scope check", () => {
    render(<AssistantGenerationStatus
      state={state({ phase: "SUBMITTING", isSubmitting: true })}
      onCancel={noop}
      onRetryConnection={noop}
      onRetrySubmission={noop}
    />);

    expect(screen.getByRole("status")).toHaveTextContent(
      "Đang kiểm tra phạm vi và dữ liệu được cấp quyền",
    );
  });
});
