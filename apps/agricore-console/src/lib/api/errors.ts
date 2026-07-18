import type { ApiErrorBody, FieldViolation } from "./types";

export class ApiClientError extends Error {
  readonly status: number;
  readonly code: string;
  readonly path: string | undefined;
  readonly traceId: string | null | undefined;
  readonly violations: FieldViolation[] | undefined;
  readonly body: ApiErrorBody | null;

  constructor(status: number, body: ApiErrorBody | null, fallbackMessage: string) {
    super(body?.message ?? fallbackMessage);
    this.name = "ApiClientError";
    this.status = status;
    this.code = body?.code ?? "UNKNOWN_ERROR";
    this.path = body?.path;
    this.traceId = body?.traceId ?? null;
    this.violations = body?.violations;
    this.body = body;
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  get isForbidden(): boolean {
    return this.status === 403;
  }
}

export async function parseApiError(response: Response): Promise<ApiClientError> {
  try {
    const body = (await response.json()) as ApiErrorBody;
    return new ApiClientError(response.status, body, response.statusText || "Request failed");
  } catch {
    return new ApiClientError(response.status, null, response.statusText || "Request failed");
  }
}
