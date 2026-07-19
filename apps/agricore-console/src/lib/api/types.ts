/** Contract-derived aliases. Regenerate with `pnpm contracts:generate`. */
import type { components as GatewayComponents } from "./generated/gateway";
import type { components as IdentityComponents } from "./generated/identity";

type GatewaySchemas = GatewayComponents["schemas"];
type GatewayParameters = GatewayComponents["parameters"];
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
export type FarmSort = GatewayParameters["FarmSort"];
export type PlotStatus = GatewaySchemas["PlotStatus"];
export type CreateFarmRequest = GatewaySchemas["CreateFarmRequest"];
export type UpdateFarmRequest = GatewaySchemas["UpdateFarmRequest"];
export type CreatePlotRequest = GatewaySchemas["CreatePlotRequest"];
export type UpdatePlotRequest = GatewaySchemas["UpdatePlotRequest"];
export type FarmResponse = GatewaySchemas["FarmResponse"];
export type PlotResponse = GatewaySchemas["PlotResponse"];
export type FarmPageResponse = GatewaySchemas["FarmPageResponse"];
export type PlotPageResponse = GatewaySchemas["PlotPageResponse"];
export type GrantFarmMembershipRequest = GatewaySchemas["GrantFarmMembershipRequest"];
export type FarmMembershipResponse = GatewaySchemas["FarmMembershipResponse"];
export type FarmMembershipPageResponse = GatewaySchemas["FarmMembershipPageResponse"];

export type CropResponse = GatewaySchemas["CropResponse"];
export type CropPageResponse = GatewaySchemas["CropPageResponse"];

export type CycleStage = GatewaySchemas["CycleStage"];
export type CycleStatus = GatewaySchemas["CycleStatus"];
export type CreateCropCycleRequest = GatewaySchemas["CreateCropCycleRequest"];
export type ChangeStageRequest = GatewaySchemas["ChangeStageRequest"];
export type CropCycleResponse = GatewaySchemas["CropCycleResponse"];
export type CropCyclePageResponse = GatewaySchemas["CropCyclePageResponse"];

export type TaskType = GatewaySchemas["TaskType"];
export type TaskStatus = GatewaySchemas["TaskStatus"];
export type CreateWorkTaskRequest = GatewaySchemas["CreateWorkTaskRequest"];
export type AssignTaskRequest = GatewaySchemas["AssignTaskRequest"];
export type CompleteTaskRequest = GatewaySchemas["CompleteTaskRequest"];
export type WorkTaskResponse = GatewaySchemas["WorkTaskResponse"];
export type WorkTaskPageResponse = GatewaySchemas["WorkTaskPageResponse"];

export type HarvestStatus = GatewaySchemas["HarvestStatus"];
export type CompleteHarvestRequest = GatewaySchemas["CompleteHarvestRequest"];
export type HarvestBatchResponse = GatewaySchemas["HarvestBatchResponse"];
export type HarvestCompletionEventStatusResponse =
  GatewaySchemas["HarvestCompletionEventStatusResponse"];
export type InventoryHarvestProjectionAcknowledgementResponse =
  GatewaySchemas["InventoryHarvestProjectionAcknowledgementResponse"];
export type TraceabilityHarvestProjectionAcknowledgementResponse =
  GatewaySchemas["TraceabilityHarvestProjectionAcknowledgementResponse"];

export type CreateTraceabilityRequest = GatewaySchemas["CreateTraceabilityRequest"];
export type PublicTraceabilityResponse = GatewaySchemas["PublicTraceabilityResponse"];
