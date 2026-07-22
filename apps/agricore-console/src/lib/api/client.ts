import type {
  AccessTokenProvider,
  AccessTokenSetter,
  ApiClientOptions,
  RequestOptions,
  SessionClearedHandler,
} from "./client-options";
import {
  requestAuthenticatedEventStream,
  type EventStreamConsumer,
  type EventStreamRequestOptions,
} from "./event-stream-request";
import { parseApiError } from "./errors";
import { requestJsonResponse } from "./json-request";
import type { LoginRequest, UserResponse, WebAuthTokensResponse } from "./types";

export type {
  AccessTokenProvider,
  AccessTokenSetter,
  ApiClientOptions,
  SessionClearedHandler,
} from "./client-options";
export type {
  EventStreamConsumer,
  EventStreamRequestOptions,
  EventStreamResponse,
} from "./event-stream-request";

class AuthOperationSupersededError extends Error {
  constructor() {
    super("Authentication operation was superseded by a newer session transition");
    this.name = "AuthOperationSupersededError";
  }
}

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
  private sessionEpoch = 0;
  private refreshFlight: {
    epoch: number;
    promise: Promise<WebAuthTokensResponse>;
  } | null = null;

  constructor(options: ApiClientOptions) {
    this.baseUrl = options.baseUrl ?? "";
    this.getAccessToken = options.getAccessToken;
    this.setAccessToken = options.setAccessToken;
    this.onSessionCleared = options.onSessionCleared;
    this.fetchImpl = options.fetchImpl ?? fetch.bind(globalThis);
    this.defaultTimeoutMs = options.defaultTimeoutMs ?? 15_000;
  }

  async webLogin(credentials: LoginRequest, signal?: AbortSignal): Promise<WebAuthTokensResponse> {
    const sessionEpoch = this.advanceSessionEpoch();
    const options: RequestOptions = {
      method: "POST",
      body: credentials,
      auth: false,
      skipAuthRefresh: true,
      ...(signal ? { signal } : {}),
    };
    const data = await this.request<WebAuthTokensResponse>("/api/v1/auth/web/login", options);
    this.assertCurrentSession(sessionEpoch);
    this.setAccessToken(data.accessToken);
    return data;
  }

  async webRefresh(signal?: AbortSignal): Promise<WebAuthTokensResponse> {
    const sessionEpoch = this.sessionEpoch;
    // Callers with an explicit cancellation contract own a separate request.
    // Bootstrap and auth-retry calls share one request so StrictMode cannot
    // rotate the HttpOnly refresh cookie twice.
    if (signal) {
      return this.performWebRefresh(sessionEpoch, signal);
    }

    if (!this.refreshFlight || this.refreshFlight.epoch !== sessionEpoch) {
      const promise = this.performWebRefresh(sessionEpoch).finally(() => {
        if (this.refreshFlight?.promise === promise) {
          this.refreshFlight = null;
        }
      });
      this.refreshFlight = { epoch: sessionEpoch, promise };
    }

    return this.refreshFlight.promise;
  }

  private async performWebRefresh(
    sessionEpoch: number,
    signal?: AbortSignal,
  ): Promise<WebAuthTokensResponse> {
    const options: RequestOptions = {
      method: "POST",
      auth: false,
      skipAuthRefresh: true,
      ...(signal ? { signal } : {}),
    };
    const data = await this.request<WebAuthTokensResponse>("/api/v1/auth/web/refresh", options);
    this.assertCurrentSession(sessionEpoch);
    this.setAccessToken(data.accessToken);
    return data;
  }

  async webLogout(signal?: AbortSignal): Promise<void> {
    this.clearSession();
    const options: RequestOptions = {
      method: "POST",
      auth: false,
      skipAuthRefresh: true,
      ...(signal ? { signal } : {}),
    };
    await this.request<void>("/api/v1/auth/web/logout", options);
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

  async withEventStream<T>(
    path: string,
    options: EventStreamRequestOptions,
    consumer: EventStreamConsumer<T>,
  ): Promise<T> {
    const sessionEpoch = this.sessionEpoch;
    const result = await requestAuthenticatedEventStream(path, options, consumer, {
      baseUrl: this.baseUrl,
      getAccessToken: this.getAccessToken,
      fetchImpl: this.fetchImpl,
      defaultTimeoutMs: this.defaultTimeoutMs,
      refreshAccessToken: () => this.refreshSingleFlight(sessionEpoch),
      clearSession: () => this.clearSessionIfCurrent(sessionEpoch),
    });
    this.assertCurrentSession(sessionEpoch);
    return result;
  }

  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const sessionEpoch = this.sessionEpoch;
    const response = await this.rawRequest(path, options);
    if (options.auth !== false) {
      this.assertCurrentSession(sessionEpoch);
    }

    if (response.status === 401 && options.auth !== false && !options.skipAuthRefresh) {
      const refreshed = await this.refreshSingleFlight(sessionEpoch);
      if (refreshed) {
        const retry = await this.rawRequest(path, { ...options, skipAuthRefresh: true });
        this.assertCurrentSession(sessionEpoch);
        if (!retry.ok) {
          throw await parseApiError(retry);
        }
        if (retry.status === 204) {
          return undefined as T;
        }
        return (await retry.json()) as T;
      }
      this.clearSessionIfCurrent(sessionEpoch);
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

  private async refreshSingleFlight(sessionEpoch: number): Promise<boolean> {
    if (!this.isCurrentSession(sessionEpoch)) {
      return false;
    }
    try {
      await this.webRefresh();
      return this.isCurrentSession(sessionEpoch);
    } catch {
      return false;
    }
  }

  private advanceSessionEpoch(): number {
    this.sessionEpoch += 1;
    return this.sessionEpoch;
  }

  private isCurrentSession(sessionEpoch: number): boolean {
    return this.sessionEpoch === sessionEpoch;
  }

  private assertCurrentSession(sessionEpoch: number): void {
    if (!this.isCurrentSession(sessionEpoch)) {
      throw new AuthOperationSupersededError();
    }
  }

  private clearSessionIfCurrent(sessionEpoch: number): void {
    if (this.isCurrentSession(sessionEpoch)) {
      this.clearSession();
    }
  }

  private clearSession(): void {
    this.advanceSessionEpoch();
    this.setAccessToken(null);
    this.onSessionCleared?.();
  }
}
