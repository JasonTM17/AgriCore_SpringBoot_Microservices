import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { RouteErrorState, RouteLoadingState } from "./route-states";

describe("route states", () => {
  it("announces route loading without exposing decorative skeletons", () => {
    const { container } = render(<RouteLoadingState />);

    expect(screen.getByRole("status", { name: "Đang tải nội dung" })).toHaveAttribute(
      "aria-busy",
      "true",
    );
    expect(container.querySelector("[aria-hidden='true']")).toBeInTheDocument();
  });

  it("renders a safe route error and offers recovery", () => {
    const reset = vi.fn();
    render(
      <RouteErrorState
        error={new Error("private stack and chunk URL")}
        reset={reset}
      />,
    );

    const alert = screen.getByRole("alert", { name: "Không thể mở trang" });
    expect(alert).not.toHaveTextContent("private stack");
    expect(alert).not.toHaveTextContent("chunk URL");

    fireEvent.click(screen.getByRole("button", { name: "Thử tải lại" }));
    expect(reset).toHaveBeenCalledOnce();
    expect(screen.getByRole("link", { name: "Tải lại toàn bộ trang" })).toHaveAttribute(
      "href",
      window.location.href,
    );
  });
});
