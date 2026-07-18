import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { App } from "./app";

describe("App", () => {
  it("renders the branded workspace readiness state", () => {
    render(<App />);

    expect(
      screen.getByRole("heading", {
        name: "Vận hành nông nghiệp trên một hệ thống thống nhất",
      }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(3);
  });
});
