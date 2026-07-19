import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { WorkTaskAssignmentForm } from "./work-task-assignment-form";

const taskCode = "TASK-IRR-001";
const employeeId = "a0000000-b000-4000-8000-c00000000009";

function renderForm(onSubmit = vi.fn()) {
  render(
    <WorkTaskAssignmentForm
      taskCode={taskCode}
      currentAssignedEmployeeId={null}
      error={null}
      isPending={false}
      isDisabled={false}
      successMessage={null}
      onSubmit={onSubmit}
      onRecoverError={vi.fn()}
    />,
  );
  return onSubmit;
}

function renderReassignmentForm(onSubmit = vi.fn()) {
  render(
    <WorkTaskAssignmentForm
      taskCode={taskCode}
      currentAssignedEmployeeId={employeeId}
      error={null}
      isPending={false}
      isDisabled={false}
      successMessage={null}
      onSubmit={onSubmit}
      onRecoverError={vi.fn()}
    />,
  );
  return onSubmit;
}

describe("WorkTaskAssignmentForm", () => {
  it("rejects malformed employee IDs before submitting a valid UUID", () => {
    const onSubmit = renderForm();
    const input = screen.getByLabelText(`ID nhân sự cho ${taskCode}`);

    fireEvent.change(input, { target: { value: "not-a-uuid" } });
    fireEvent.click(screen.getByRole("button", { name: `Xác nhận phân công ${taskCode}` }));
    expect(screen.getByRole("alert")).toHaveTextContent("UUID hợp lệ");
    expect(onSubmit).not.toHaveBeenCalled();

    fireEvent.change(input, { target: { value: ` ${employeeId} ` } });
    fireEvent.click(screen.getByRole("button", { name: `Xác nhận phân công ${taskCode}` }));
    expect(onSubmit).toHaveBeenCalledWith(employeeId);
  });

  it("treats case-only UUID changes as the same assignment", () => {
    const onSubmit = renderReassignmentForm();
    fireEvent.change(screen.getByLabelText(`ID nhân sự cho ${taskCode}`), {
      target: { value: employeeId.toUpperCase() },
    });

    expect(screen.getByRole("button", {
      name: `Xác nhận phân công ${taskCode}`,
    })).toBeDisabled();
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
