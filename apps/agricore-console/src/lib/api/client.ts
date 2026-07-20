import type {
  AccessTokenProvider,
  AccessTokenSetter,
  ApiClientOptions,
  RequestOptions,
  SessionClearedHandler,
} from "./client-options";
import { parseApiError } from "./errors";
import { requestJsonResponse } from "./json-request";
import type { LoginRequest, UserResponse, WebAuthTokensResponse } from "./types";

export type {
  AccessTokenProvider,
  AccessTokenSetter,
  ApiClientOptions,
  SessionClearedHandler,
} from "./client-options";

/**
 * Gateway-facing fetch helper.
 * - credentials: include for web cookie refresh
 * - single-flight 401 refresh queue
 * - never stores refresh token in JS
 */
export class ApiClient {
  private readonly baseUrl: string;
  private readonly getAccessToken: AccessTokenProvider;
  private readonly setAccessToken: AccessTokenSetter;
  private readonly onSessionCleared: SessionClearedHandler | undefined;
  private readonly fetchImpl: typeof fetch;
  private readonly defaultTimeoutMs: number;
  private refreshPromise: Promise<WebAuthTokensResponse> | null = null;

  constructor(options: ApiClientOptions) {
    this.baseUrl = options.baseUrl ?? "";
    this.getAccessToken = options.getAccessToken;
    this.setAccessToken = options.setAccessToken;
    this.onSessionCleared = options.onSessionCleared;
    this.fetchImpl = options.fetchImpl ?? fetch.bind(globalThis);
    this.defaultTimeoutMs = options.defaultTimeoutMs ?? 15_000;
  }

  async webLogin(credentials: LoginRequest, signal?: AbortSignal): Promise<WebAuthTokensResponse> {
    const options: RequestOptions = {
      method: "POST",
      body: credentials,
      auth: false,
      skipAuthRefresh: true,
      ...(signal ? { signal } : {}),
    };
    const data = await this.request<WebAuthTokensResponse>("/api/v1/auth/web/login", options);
    this.setAccessToken(data.accessToken);
    return data;
  }

  async webRefresh(signal?: AbortSignal): Promise<WebAuthTokensResponse> {
    // Callers with an explicit cancellation contract own a separate request.
    // Bootstrap and auth-retry calls share one request so StrictMode cannot
    // rotate the HttpOnly refresh cookie twice.
    if (signal) {
      return this.performWebRefresh(signal);
    }

    if (!this.refreshPromise) {
      this.refreshPromise = this.performWebRefresh().finally(() => {
        this.refreshPromise = null;
      });
    }

    return this.refreshPromise;
  }

  private async performWebRefresh(signal?: AbortSignal): Promise<WebAuthTokensResponse> {
    const options: RequestOptions = {
      method: "POST",
      auth: false,
      skipAuthRefresh: true,
      ...(signal ? { signal } : {}),
    };
    const data = await this.request<WebAuthTokensResponse>("/api/v1/auth/web/refresh", options);
    this.setAccessToken(data.accessToken);
    return data;
  }

  async webLogout(signal?: AbortSignal): Promise<void> {
    const options: RequestOptions = {
      method: "POST",
      auth: false,
      skipAuthRefresh: true,
      ...(signal ? { signal } : {}),
    };
    try {
      await this.request<void>("/api/v1/auth/web/logout", options);
    } finally {
      this.setAccessToken(null);
      this.onSessionCleared?.();
    }
  }

  async getCurrentUser(signal?: AbortSignal): Promise<UserResponse> {
    return this.request<UserResponse>("/api/v1/users/me", {
      method: "GET",
      auth: true,
      ...(signal ? { signal } : {}),
    });
  }

  async publicGet<T>(path: string, signal?: AbortSignal): Promise<T> {
    return this.request<T>(path, {
      method: "GET",
      auth: false,
      skipAuthRefresh: true,
      credentials: "omit",
      cache: "no-store",
      ...(signal ? { signal } : {}),
    });
  }

  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const response = await this.rawRequest(path, options);

    if (response.status === 401 && options.auth !== false && !options.skipAuthRefresh) {
      const refreshed = await this.refreshSingleFlight();
      if (refreshed) {
        const retry = await this.rawRequest(path, { ...options, skipAuthRefresh: true });
        if (!retry.ok) {
          throw await parseApiError(retry);
        }
        if (retry.status === 204) {
          return undefined as T;
        }
        return (await retry.json()) as T;
      }
      this.setAccessToken(null);
      this.onSessionCleared?.();
      throw await parseApiError(response);
    }

    if (!response.ok) {
      throw await parseApiError(response);
    }

    if (response.status === 204) {
      return undefined as T;
    }

    return (await response.json()) as T;
  }

  private async rawRequest(path: string, options: RequestOptions): Promise<Response> {
    return requestJsonResponse(path, options, {
      baseUrl: this.baseUrl,
      getAccessToken: this.getAccessToken,
      fetchImpl: this.fetchImpl,
      defaultTimeoutMs: this.defaultTimeoutMs,
    });
  }

  private async refreshSingleFlight(): Promise<boolean> {
    try {
      await this.webRefresh();
      return true;
    } catch {
      return false;
    }
  }
}
