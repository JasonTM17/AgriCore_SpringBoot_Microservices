import type { ApiClient } from "../../lib/api/client";
import type { components as IdentityComponents } from "../../lib/api/generated/identity";

type IdentitySchemas = IdentityComponents["schemas"];

export type AdminUser = IdentitySchemas["UserResponse"];
export type AdminUserPage = IdentitySchemas["UserPageResponse"];
export type AdminRoleCode = IdentitySchemas["RoleCode"];

export interface UpdateAdminUserRolesRequest {
  roles: AdminRoleCode[];
}

export function listAdminUsers(
  api: ApiClient,
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<AdminUserPage> {
  const search = new URLSearchParams({ page: String(page), size: String(size) });
  return api.request<AdminUserPage>(`/api/v1/admin/users?${search.toString()}`, {
    method: "GET",
    ...(signal ? { signal } : {}),
  });
}

export function updateAdminUserRoles(
  api: ApiClient,
  userId: string,
  request: UpdateAdminUserRolesRequest,
  signal?: AbortSignal,
): Promise<AdminUser> {
  return api.request<AdminUser>(
    `/api/v1/admin/users/${encodeURIComponent(userId)}/roles`,
    {
      method: "PATCH",
      body: request,
      ...(signal ? { signal } : {}),
    },
  );
}
