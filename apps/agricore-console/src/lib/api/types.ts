/** Contract-derived aliases. Regenerate with `pnpm contracts:generate`. */
import type { components } from "./generated/identity";

type IdentitySchemas = components["schemas"];

export type UserStatus = IdentitySchemas["UserResponse"]["status"];
export type RoleCode = IdentitySchemas["RoleCode"];
export type UserResponse = IdentitySchemas["UserResponse"];
export type WebAuthTokensResponse = IdentitySchemas["WebAuthTokensResponse"];
export type LoginRequest = IdentitySchemas["LoginRequest"];
export type FieldViolation = IdentitySchemas["FieldViolation"];
export type ApiErrorBody = IdentitySchemas["ApiError"];
export type UserPageResponse = IdentitySchemas["UserPageResponse"];
export type UpdateUserRolesRequest = IdentitySchemas["UpdateUserRolesRequest"];
