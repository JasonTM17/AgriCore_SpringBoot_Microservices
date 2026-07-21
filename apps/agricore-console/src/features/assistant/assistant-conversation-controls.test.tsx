import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { FarmResponse } from "../../lib/api/types";
import {
  assistantConversationPage,
  TEST_ASSISTANT_CONVERSATION_ID,
  TEST_ASSISTANT_FARM_ID,
} from "./assistant-conversation-test-fixtures";
import { AssistantConversationSidebar } from "./assistant-conversation-sidebar";
import { AssistantNewConversationForm } from "./assistant-new-conversation-form";

const ACTIVE_FARM = {
  id: TEST_ASSISTANT_FARM_ID,
  code: "FARM-DL-01",
  name: "Nông trại Đắk Lắk",
  address: null,
  province: "Đắk Lắk",
  totalAreaHa: 120,
  latitude: null,
  longitude: null,
  status: "ACTIVE",
  createdAt: "2026-07-21T00:00:00Z",
  updatedAt: "2026-07-21T00:00:00Z",
  version: 0,
} satisfies FarmResponse;

describe("assistant conversation controls", () => {
  it("submits the verified active farm context and can switch to enterprise", () => {
    const onSubmit = vi.fn();
    render(
      <AssistantNewConversationForm
        activeFarm={ACTIVE_FARM}
        error={null}
        isPending={false}
        onSubmit={onSubmit}
      />,
    );

    fireEvent.change(screen.getByLabelText("Tên hội thoại"), {
      target: { value: "Theo dõi mùa vụ" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Tạo hội thoại" }));
    expect(onSubmit).toHaveBeenLastCalledWith({
      title: "Theo dõi mùa vụ",
      contextType: "FARM",
      farmId: TEST_ASSISTANT_FARM_ID,
    });

    fireEvent.change(screen.getByLabelText("Phạm vi dữ liệu"), {
      target: { value: "ENTERPRISE" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Tạo hội thoại" }));
    expect(onSubmit).toHaveBeenLastCalledWith({
      title: "Theo dõi mùa vụ",
      contextType: "ENTERPRISE",
      farmId: null,
    });
  });

  it("selects a conversation and switches list status explicitly", () => {
    const onSelect = vi.fn();
    const onStatusChange = vi.fn();
    render(
      <AssistantConversationSidebar
        data={assistantConversationPage()}
        error={null}
        isFetching={false}
        isPending={false}
        selectedConversationId={null}
        status="OPEN"
        onNext={vi.fn()}
        onPrevious={vi.fn()}
        onRetry={vi.fn()}
        onSelect={onSelect}
        onStatusChange={onStatusChange}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: /Theo dõi mùa vụ/ }));
    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({
      id: TEST_ASSISTANT_CONVERSATION_ID,
    }));
    fireEvent.click(screen.getByRole("button", { name: "Đã lưu trữ" }));
    expect(onStatusChange).toHaveBeenCalledWith("ARCHIVED");
  });
});
