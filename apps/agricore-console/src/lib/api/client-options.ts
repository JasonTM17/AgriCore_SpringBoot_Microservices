export type AccessTokenProvider = () => string | null;
export type AccessTokenSetter = (token: string | null) => void;
export type SessionClearedHandler = () => void;

export interface ApiClientOptions {
  baseUrl?: string;
  getAccessToken: AccessTokenProvider;
  setAccessToken: AccessTokenSetter;
  onSessionCleared?: SessionClearedHandler;
  fetchImpl?: typeof fetch;
  defaultTimeoutMs?: number;
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  auth?: boolean;
  signal?: AbortSignal;
  timeoutMs?: number;
  headers?: Record<string, string>;
  credentials?: RequestCredentials;
  cache?: RequestCache;
  /** Skip 401 -> refresh -> retry (used by refresh itself). */
  skipAuthRefresh?: boolean;
}
