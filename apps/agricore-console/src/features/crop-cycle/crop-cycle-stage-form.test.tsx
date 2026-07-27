import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { CropCycleStageForm } from "./crop-cycle-stage-form";

describe("CropCycleStageForm", () => {
  it("blocks notes beyond the server contract boundary", () => {
    const onSubmit = vi.fn();
    render(
      <CropCycleStageForm
        cycleCode="CYCLE-001"
        allowedStages={["HARVESTING"]}
        isPending={false}
        isDisabled={false}
        onSubmit={onSubmit}
      />,
    );

    const notes = screen.getByRole("textbox");
    expect(notes).toHaveAttribute("maxLength", "2000");

    fireEvent.change(screen.getByRole("combobox"), { target: { value: "HARVESTING" } });
    fireEvent.change(notes, { target: { value: "x".repeat(2_001) } });

    expect(screen.getByRole("alert")).toHaveTextContent("2.000 ký tự");
    expect(screen.getByRole("button")).toBeDisabled();
    fireEvent.submit(notes.closest("form")!);
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("submits notes at the exact server contract boundary", () => {
    const onSubmit = vi.fn();
    render(
      <CropCycleStageForm
        cycleCode="CYCLE-001"
        allowedStages={["HARVESTING"]}
        isPending={false}
        isDisabled={false}
        onSubmit={onSubmit}
      />,
    );

    fireEvent.change(screen.getByRole("combobox"), { target: { value: "HARVESTING" } });
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "x".repeat(2_000) } });
    fireEvent.click(screen.getByRole("button"));

    expect(onSubmit).toHaveBeenCalledWith("HARVESTING", "x".repeat(2_000));
  });

  it("preserves notes but clears a destination that is no longer legal", () => {
    const onSubmit = vi.fn();
    const { rerender } = render(
      <CropCycleStageForm
        cycleCode="CYCLE-001"
        allowedStages={["HARVESTING"]}
        isPending={false}
        isDisabled={false}
        onSubmit={onSubmit}
      />,
    );
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "HARVESTING" } });
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "draft note" } });

    rerender(
      <CropCycleStageForm
        cycleCode="CYCLE-001"
        allowedStages={["COMPLETED"]}
        isPending={false}
        isDisabled={false}
        onSubmit={onSubmit}
      />,
    );

    expect(screen.getByRole("combobox")).toHaveValue("");
    expect(screen.getByRole("textbox")).toHaveValue("draft note");
  });

  it("names the cycle and irreversible effect before cancellation", () => {
    const confirmMock = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true);
    const onSubmit = vi.fn();
    render(
      <CropCycleStageForm
        cycleCode="CYCLE-001"
        allowedStages={["CANCELLED"]}
        isPending={false}
        isDisabled={false}
        onSubmit={onSubmit}
      />,
    );
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "CANCELLED" } });

    fireEvent.click(screen.getByRole("button"));
    expect(confirmMock).toHaveBeenLastCalledWith(
      "Hủy mùa vụ CYCLE-001? Mùa vụ sẽ kết thúc và không thể chuyển sang giai đoạn khác.",
    );
    expect(onSubmit).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button"));
    expect(onSubmit).toHaveBeenCalledWith("CANCELLED", null);
  });
});
