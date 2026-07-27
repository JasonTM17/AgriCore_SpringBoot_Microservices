import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ResponsiveShowcaseImage } from "./responsive-showcase-image";
import { farmSunriseShowcase } from "./showcase-media";

describe("ResponsiveShowcaseImage", () => {
  it("renders responsive sources with reserved dimensions and lazy defaults", () => {
    const { container } = render(
      <ResponsiveShowcaseImage
        asset={farmSunriseShowcase}
        alt="Nông trại lúc bình minh"
        sizes="(min-width: 1024px) 60vw, 100vw"
      />,
    );

    const image = screen.getByRole("img", { name: "Nông trại lúc bình minh" });
    const source = container.querySelector("source");

    expect(source).toHaveAttribute(
      "srcset",
      expect.stringContaining("/agricore-farm-sunrise-480w.webp 480w"),
    );
    expect(source).toHaveAttribute("sizes", "(min-width: 1024px) 60vw, 100vw");
    expect(image).toHaveAttribute("src", "/agricore-farm-sunrise.webp");
    expect(image).toHaveAttribute("width", "1672");
    expect(image).toHaveAttribute("height", "941");
    expect(image).toHaveAttribute("loading", "lazy");
    expect(image).toHaveAttribute("decoding", "async");
  });

  it("replaces a failed request with an accessible deterministic fallback", () => {
    render(
      <ResponsiveShowcaseImage
        asset={farmSunriseShowcase}
        alt="Nông trại lúc bình minh"
        sizes="100vw"
      />,
    );

    fireEvent.error(screen.getByRole("img", { name: "Nông trại lúc bình minh" }));

    expect(
      screen.getByRole("img", {
        name: "Nông trại lúc bình minh. Không thể tải hình ảnh.",
      }),
    ).toBeInTheDocument();
    expect(screen.queryByAltText("Nông trại lúc bình minh")).not.toBeInTheDocument();
  });
});
