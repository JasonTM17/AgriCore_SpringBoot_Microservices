/** Contract-derived aliases. Regenerate with `pnpm contracts:generate`. */
import type { components as GatewayComponents } from "./generated/gateway";
import type { components as IdentityComponents } from "./generated/identity";

type GatewaySchemas = GatewayComponents["schemas"];
type IdentitySchemas = IdentityComponents["schemas"];

export type UserStatus = IdentitySchemas["UserResponse"]["status"];
export type RoleCode = IdentitySchemas["RoleCode"];
export type UserResponse = IdentitySchemas["UserResponse"];
export type WebAuthTokensResponse = IdentitySchemas["WebAuthTokensResponse"];
export type LoginRequest = IdentitySchemas["LoginRequest"];
export type FieldViolation = IdentitySchemas["FieldViolation"];
export type ApiErrorBody = IdentitySchemas["ApiError"];
export type UserPageResponse = IdentitySchemas["UserPageResponse"];
export type UpdateUserRolesRequest = IdentitySchemas["UpdateUserRolesRequest"];

export type FarmStatus = GatewaySchemas["FarmStatus"];
export type PlotStatus = GatewaySchemas["PlotStatus"];
export type CreateFarmRequest = GatewaySchemas["CreateFarmRequest"];
export type UpdateFarmRequest = GatewaySchemas["UpdateFarmRequest"];
export type CreatePlotRequest = GatewaySchemas["CreatePlotRequest"];
export type UpdatePlotRequest = GatewaySchemas["UpdatePlotRequest"];
export type FarmResponse = GatewaySchemas["FarmResponse"];
export type PlotResponse = GatewaySchemas["PlotResponse"];
export type FarmPageResponse = GatewaySchemas["FarmPageResponse"];
export type PlotPageResponse = GatewaySchemas["PlotPageResponse"];
