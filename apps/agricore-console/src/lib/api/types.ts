/** Contract-aligned types for identity auth (see contracts/openapi/identity-service.v1.yaml). */

export type UserStatus = "ACTIVE" | "LOCKED" | "DISABLED";

export type RoleCode =
  | "SYSTEM_ADMIN"
  | "FARM_MANAGER"
  | "AGRONOMIST"
  | "FIELD_WORKER"
  | "WAREHOUSE_MANAGER"
  | "SALES_STAFF"
  | "AUDITOR";

export interface UserResponse {
  id: string;
  email: string;
  fullName: string;
  status: UserStatus;
  roles: string[];
  lastLoginAt: string | null;
  createdAt: string;
}

export interface WebAuthTokensResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserResponse;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface FieldViolation {
  field: string;
  message: string;
  rejectedValue?: unknown;
}

export interface ApiErrorBody {
  timestamp?: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path?: string;
  traceId?: string | null;
  violations?: FieldViolation[];
}
