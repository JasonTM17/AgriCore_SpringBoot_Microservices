import { describe, expect, it } from "vitest";

import { lazyRouteComponents } from "./lazy-route-components";

describe("lazy route components", () => {
  it.each(Object.entries(lazyRouteComponents))(
    "keeps %s in a preloadable route chunk",
    (_name, component) => {
      expect(component.preload).toEqual(expect.any(Function));
    },
  );
});
