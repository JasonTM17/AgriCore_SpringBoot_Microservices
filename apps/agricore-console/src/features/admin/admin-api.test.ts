import { describe, expect, it, vi } from "vitest";

import { ApiClient } from "../../lib/api/client";
import { listAdminUsers, updateAdminUserRoles } from "./admin-api";

type FetchFn = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

describe("admin API", () => {
  it("requests paginated users and patches an exact role set", async () => {
    const fetchImpl: FetchFn = vi.fn(() => Promise.resolve(new Response("{}", { status: 200 })));
    const api = new ApiClient({ getAccessToken: () => "token", setAccessToken: () => undefined, fetchImpl });
    const request = { roles: ["FARM_MANAGER", "AUDITOR"] as const };
    await listAdminUsers(api, 2, 20);
    await updateAdminUserRoles(api, "user/id", { roles: [...request.roles] });
    expect(vi.mocked(fetchImpl).mock.calls.map(([input, init]) => [input, init?.method])).toEqual([
      ["/api/v1/admin/users?page=2&size=20", "GET"],
      ["/api/v1/admin/users/user%2Fid/roles", "PATCH"],
    ]);
    expect(vi.mocked(fetchImpl).mock.calls[1]?.[1]?.body).toBe(JSON.stringify({ roles: [...request.roles] }));
  });
});
