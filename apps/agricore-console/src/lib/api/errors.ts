import type { ApiErrorBody, FieldViolation } from "./types";

export class ApiClientError extends Error {
  readonly status: number;
  readonly code: string;
  readonly path: string | undefined;
  readonly traceId: string | null;
  readonly correlationId: string | null;
  readonly violations: FieldViolation[] | undefined;
  readonly body: ApiErrorBody | null;

  constructor(
    status: number,
    body: ApiErrorBody | null,
    fallbackMessage: string,
    context: ApiClientErrorContext = {},
  ) {
    super(body?.message ?? fallbackMessage);
    this.name = "ApiClientError";
    this.status = status;
    this.code = body?.code ?? context.fallbackCode ?? "UNKNOWN_ERROR";
    this.path = body?.path;
    this.traceId = body?.traceId ?? context.traceId ?? null;
    this.correlationId = context.correlationId ?? null;
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

export interface ApiClientErrorContext {
  fallbackCode?: string;
  traceId?: string | null;
  correlationId?: string | null;
}

export async function parseApiError(response: Response): Promise<ApiClientError> {
  const context: ApiClientErrorContext = {
    traceId: response.headers.get("X-Trace-ID"),
    correlationId:
      response.headers.get("X-Correlation-ID") ?? response.headers.get("X-Request-ID"),
  };
  try {
    const body = (await response.json()) as ApiErrorBody;
    return new ApiClientError(
      response.status,
      body,
      response.statusText || "Request failed",
      context,
    );
  } catch {
    return new ApiClientError(
      response.status,
      null,
      response.statusText || "Request failed",
      context,
    );
  }
}
