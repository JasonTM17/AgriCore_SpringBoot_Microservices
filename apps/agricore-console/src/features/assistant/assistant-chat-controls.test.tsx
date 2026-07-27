import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import {
  assistantMessage,
  assistantMessagePage,
} from "./assistant-conversation-test-fixtures";
import { AssistantComposer } from "./assistant-composer";
import { createAssistantGenerationProjection } from "./assistant-generation-projection";
import { AssistantTranscript } from "./assistant-transcript";

const MALICIOUS_TEXT = '<img src=x onerror="stealToken()">';

describe("assistant chat controls", () => {
  it("uses Enter to send, preserves Shift+Enter, and clears only accepted prompts", async () => {
    const onSend = vi.fn<(prompt: string) => Promise<boolean>>()
      .mockResolvedValueOnce(true)
      .mockResolvedValueOnce(false);
    render(
      <AssistantComposer
        disabled={false}
        disabledReason={null}
        isSubmitting={false}
        onSend={onSend}
      />,
    );
    const textarea = screen.getByLabelText("Câu hỏi cho trợ lý");

    fireEvent.change(textarea, { target: { value: "Kiểm tra mùa vụ" } });
    fireEvent.keyDown(textarea, { key: "Enter", shiftKey: true });
    expect(onSend).not.toHaveBeenCalled();
    fireEvent.keyDown(textarea, { key: "Enter" });
    await waitFor(() => expect(onSend).toHaveBeenCalledWith("Kiểm tra mùa vụ"));
    await waitFor(() => expect(textarea).toHaveValue(""));

    fireEvent.change(textarea, { target: { value: "Giữ lại khi lỗi" } });
    fireEvent.click(screen.getByRole("button", { name: "Gửi câu hỏi" }));
    await waitFor(() => expect(onSend).toHaveBeenCalledTimes(2));
    expect(textarea).toHaveValue("Giữ lại khi lỗi");
  });

  it("renders model content as text and suppresses a persisted live duplicate", () => {
    const generationId = "80000000-0000-0000-0000-000000000001";
    const data = assistantMessagePage({
      totalElements: 1,
      content: [assistantMessage({
        role: "ASSISTANT",
        generationId,
        content: MALICIOUS_TEXT,
      })],
    });
    const projection = {
      ...createAssistantGenerationProjection(generationId),
      status: "COMPLETED" as const,
      draft: "Không được hiển thị hai lần",
    };
    const { container, rerender } = render(
      <AssistantTranscript
        canGoNewer={false}
        canGoOlder={false}
        data={data}
        error={null}
        isAtLatest
        isFetching={false}
        isPending={false}
        pendingPrompt={null}
        projection={projection}
        onLatest={vi.fn()}
        onNewer={vi.fn()}
        onOlder={vi.fn()}
        onRetry={vi.fn()}
      />,
    );

    expect(screen.getByText(MALICIOUS_TEXT)).toBeInTheDocument();
    expect(container.querySelector("img")).toBeNull();
    expect(screen.queryByLabelText("Phản hồi đang tạo")).not.toBeInTheDocument();

    rerender(
      <AssistantTranscript
        canGoNewer={false}
        canGoOlder={false}
        data={data}
        error={null}
        isAtLatest
        isFetching={false}
        isPending={false}
        pendingPrompt={null}
        projection={{
          ...createAssistantGenerationProjection(
            "80000000-0000-0000-0000-000000000002",
          ),
          draft: "Bản nháp trực tiếp",
        }}
        onLatest={vi.fn()}
        onNewer={vi.fn()}
        onOlder={vi.fn()}
        onRetry={vi.fn()}
      />,
    );
    expect(screen.getByLabelText("Phản hồi đang tạo")).toHaveTextContent("Bản nháp trực tiếp");
  });
});
