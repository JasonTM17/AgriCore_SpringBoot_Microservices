import { describe, expect, it } from "vitest";

import { workTaskQueryKeys } from "./work-task-query-keys";

const params = {
  cropCycleId: "50000000-0000-0000-0000-000000000001",
  plotId: "30000000-0000-0000-0000-000000000001",
  page: 0,
  size: 20,
};

describe("work-task query keys", () => {
  it("isolates list and detail caches by authenticated subject", () => {
    expect(workTaskQueryKeys.list("user-a", params)).not.toEqual(
      workTaskQueryKeys.list("user-b", params),
    );
    expect(workTaskQueryKeys.detail("user-a", "task-1")).not.toEqual(
      workTaskQueryKeys.detail("user-b", "task-1"),
    );
  });

  it("provides a cycle-list prefix without dropping plot scope from exact keys", () => {
    const prefix = workTaskQueryKeys.cycleLists("user-a", params.cropCycleId);
    const exact = workTaskQueryKeys.list("user-a", params);

    expect(exact.slice(0, prefix.length)).toEqual(prefix);
    expect(exact).toContain(params.plotId);
  });
});
