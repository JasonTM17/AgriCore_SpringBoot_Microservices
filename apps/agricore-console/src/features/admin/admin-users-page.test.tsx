import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AdminUsersPage } from "./admin-users-page";

const { requestMock } = vi.hoisted(() => ({
  requestMock: vi.fn(),
}));

vi.mock("../../lib/auth/session", () => ({
  useSession: () => ({
    api: { request: requestMock },
    user: {
      permissions: ["IDENTITY_USER_READ", "IDENTITY_USER_ADMIN"],
    },
  }),
}));

const users = [
  {
    id: "11111111-1111-1111-1111-111111111111",
    email: "manager@agricore.local",
    fullName: "Nguyễn Minh An",
    status: "ACTIVE",
    roles: ["FARM_MANAGER"],
    permissions: ["IDENTITY_USER_READ"],
    lastLoginAt: null,
    createdAt: "2026-07-18T00:00:00Z",
  },
  {
    id: "22222222-2222-2222-2222-222222222222",
    email: "auditor@agricore.local",
    fullName: "Trần Thu Hà",
    status: "ACTIVE",
    roles: ["AUDITOR"],
    permissions: ["IDENTITY_USER_READ"],
    lastLoginAt: null,
    createdAt: "2026-07-18T00:00:00Z",
  },
] as const;

describe("AdminUsersPage", () => {
  beforeEach(() => {
    requestMock.mockReset();
    requestMock.mockResolvedValue({
      content: users,
      page: 0,
      size: 20,
      totalElements: users.length,
      totalPages: 1,
      first: true,
      last: true,
    });
  });

  it("uses valid table markup with native buttons for user selection", async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={queryClient}>
        <AdminUsersPage />
      </QueryClientProvider>,
    );

    const first = await screen.findByRole("button", {
      name: "Chọn người dùng Nguyễn Minh An",
    });
    const second = screen.getByRole("button", {
      name: "Chọn người dùng Trần Thu Hà",
    });

    expect(first.tagName).toBe("BUTTON");
    expect(first.closest("td")).not.toBeNull();
    expect(first).toHaveAttribute("aria-pressed", "true");
    expect(second).toHaveAttribute("aria-pressed", "false");

    fireEvent.click(second);

    expect(second).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("heading", { name: "Trần Thu Hà" })).toBeInTheDocument();
  });
});
