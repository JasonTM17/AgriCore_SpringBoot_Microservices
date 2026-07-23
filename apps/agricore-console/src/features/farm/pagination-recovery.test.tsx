import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { FarmPageResponse, FarmResponse, PlotPageResponse } from "../../lib/api/types";
import { FarmListPanel } from "./farm-list-panel";
import { PlotListPanel } from "./plot-list-panel";

const farm = {
  id: "20000000-0000-0000-0000-000000000001",
  code: "FARM-DL-01",
  enterpriseId: null,
  name: "Nông trại Đắk Lắk",
  address: null,
  province: "Đắk Lắk",
  totalAreaHa: 120,
  latitude: null,
  longitude: null,
  status: "ACTIVE",
  createdAt: "2026-07-19T00:00:00Z",
  updatedAt: "2026-07-19T00:00:00Z",
  version: 0,
} satisfies FarmResponse;

const emptyFarmPage = {
  content: [],
  page: 1,
  size: 20,
  totalElements: 21,
  totalPages: 2,
  first: false,
  last: true,
} satisfies FarmPageResponse;

const emptyPlotPage = {
  content: [],
  page: 2,
  size: 20,
  totalElements: 41,
  totalPages: 3,
  first: false,
  last: true,
} satisfies PlotPageResponse;

describe("Farm pagination recovery", () => {
  it("keeps backward navigation when the current farm page becomes empty", () => {
    const onPrevious = vi.fn();
    render(
      <FarmListPanel
        data={emptyFarmPage}
        error={null}
        isPending={false}
        isFetching={false}
        activeFarmId={null}
        onSelect={vi.fn()}
        onRetry={vi.fn()}
        onPrevious={onPrevious}
        onNext={vi.fn()}
      />,
    );

    expect(screen.getByText("Trang nông trại này không còn dữ liệu")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Về trang trước" }));
    expect(onPrevious).toHaveBeenCalledOnce();
  });

  it("keeps backward navigation when the current plot page becomes empty", () => {
    const onPrevious = vi.fn();
    render(
      <PlotListPanel
        farm={farm}
        data={emptyPlotPage}
        error={null}
        isPending={false}
        isFetching={false}
        waitingForFarm={false}
        onRetry={vi.fn()}
        onPrevious={onPrevious}
        onNext={vi.fn()}
      />,
    );

    expect(screen.getByText("Trang lô canh tác này không còn dữ liệu")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Về trang trước" }));
    expect(onPrevious).toHaveBeenCalledOnce();
  });
});
