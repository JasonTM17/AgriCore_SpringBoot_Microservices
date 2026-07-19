import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { WorkTaskCreateForm } from "./work-task-create-form";

function renderForm(onSubmit = vi.fn()) {
  render(
    <WorkTaskCreateForm
      cycleCode="CYCLE-1"
      error={null}
      isPending={false}
      isDisabled={false}
      onSubmit={onSubmit}
      onRecoverError={vi.fn()}
    />,
  );
  return onSubmit;
}

function fillRequiredFields() {
  fireEvent.change(screen.getByLabelText("Mã công việc"), { target: { value: " task-001 " } });
  fireEvent.change(screen.getByLabelText("Loại công việc"), { target: { value: "IRRIGATION" } });
  fireEvent.change(screen.getByLabelText("Tiêu đề công việc"), { target: { value: " Tưới khu A " } });
}

describe("WorkTaskCreateForm", () => {
  it("normalizes optional values and local schedule instants", () => {
    const onSubmit = renderForm();
    fillRequiredFields();
    fireEvent.change(screen.getByLabelText("Bắt đầu dự kiến"), {
      target: { value: "2026-07-20T08:00" },
    });
    fireEvent.change(screen.getByLabelText("Kết thúc dự kiến"), {
      target: { value: "2026-07-20T09:30" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Tạo công việc" }));

    expect(onSubmit).toHaveBeenCalledWith({
      code: "task-001",
      taskType: "IRRIGATION",
      title: "Tưới khu A",
      description: null,
      priority: "MEDIUM",
      scheduledStart: new Date("2026-07-20T08:00").toISOString(),
      scheduledEnd: new Date("2026-07-20T09:30").toISOString(),
    });
  });

  it("blocks a schedule whose end precedes its start", () => {
    const onSubmit = renderForm();
    fillRequiredFields();
    fireEvent.change(screen.getByLabelText("Bắt đầu dự kiến"), {
      target: { value: "2026-07-20T10:00" },
    });
    fireEvent.change(screen.getByLabelText("Kết thúc dự kiến"), {
      target: { value: "2026-07-20T09:00" },
    });

    expect(screen.getByRole("alert")).toHaveTextContent("không được trước");
    expect(screen.getByRole("button", { name: "Tạo công việc" })).toBeDisabled();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("reflects server-backed field length limits", () => {
    renderForm();
    expect(screen.getByLabelText("Mã công việc")).toHaveAttribute("maxLength", "64");
    expect(screen.getByLabelText("Tiêu đề công việc")).toHaveAttribute("maxLength", "200");
  });
});
