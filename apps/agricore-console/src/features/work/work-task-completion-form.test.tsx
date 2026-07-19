import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { WorkTaskCompletionForm } from "./work-task-completion-form";

const taskCode = "TASK-IRR-001";

function renderForm(onSubmit = vi.fn()) {
  render(
    <WorkTaskCompletionForm
      taskCode={taskCode}
      currentNotes={null}
      error={null}
      isPending={false}
      isDisabled={false}
      onSubmit={onSubmit}
      onRecoverError={vi.fn()}
    />,
  );
  return onSubmit;
}

describe("WorkTaskCompletionForm", () => {
  it("trims completion notes before submitting", () => {
    const onSubmit = renderForm();
    fireEvent.change(screen.getByLabelText(`Ghi chú hoàn tất cho ${taskCode}`), {
      target: { value: "  Đã kiểm tra độ ẩm sau tưới.  " },
    });
    fireEvent.click(screen.getByRole("button", { name: `Xác nhận hoàn tất ${taskCode}` }));

    expect(onSubmit).toHaveBeenCalledWith({ notes: "Đã kiểm tra độ ẩm sau tưới." });
  });

  it("submits null when optional notes are empty", () => {
    const onSubmit = renderForm();
    fireEvent.click(screen.getByRole("button", { name: `Xác nhận hoàn tất ${taskCode}` }));

    expect(onSubmit).toHaveBeenCalledWith({ notes: null });
  });

  it("mirrors the persisted notes length boundary", () => {
    renderForm();

    expect(screen.getByLabelText(`Ghi chú hoàn tất cho ${taskCode}`)).toHaveAttribute(
      "maxLength",
      "2000",
    );
  });
});
