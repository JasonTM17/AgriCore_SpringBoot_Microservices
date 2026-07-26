import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { DashboardPage } from "./dashboard-page";

vi.mock("../../lib/auth/session", () => ({
  useSession: () => ({
    user: {
      fullName: "Quản lý nông trại",
      email: "manager@agricore.local",
      roles: ["FARM_MANAGER"],
    },
  }),
}));

describe("DashboardPage", () => {
  it("renders repository-owned showcase images and the bounded GIF", () => {
    render(<DashboardPage />);

    expect(
      screen.getByRole("heading", { name: "Một nền tảng, toàn bộ hành trình nông sản" }),
    ).toBeInTheDocument();
    expect(screen.getByAltText("Nông trại cao nguyên vào lúc bình minh"))
      .toHaveAttribute("src", "/agricore-farm-sunrise.webp");
    expect(screen.getByAltText("Nông trại cao nguyên vào lúc bình minh"))
      .toHaveAttribute("srcset", expect.stringContaining("/agricore-farm-sunrise-960w.webp 960w"));
    expect(screen.getByAltText("Nông trại cao nguyên vào lúc bình minh"))
      .toHaveAttribute("fetchpriority", "high");
    expect(screen.getByAltText("Ba khung hình giới thiệu trang trại, thu hoạch và truy xuất"))
      .toHaveAttribute("src", "/agricore-farm-story.gif");
    expect(screen.getByText("manager@agricore.local")).toBeInTheDocument();
  });
});
