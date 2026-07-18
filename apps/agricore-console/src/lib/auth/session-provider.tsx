import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import { ApiClient } from "../api/client";
import { ApiClientError } from "../api/errors";
import type { LoginRequest, UserResponse } from "../api/types";
import { SessionContext, type SessionContextValue, type SessionStatus } from "./session-context";

class AccessTokenStore {
  #value: string | null = null;

  get(): string | null {
    return this.#value;
  }

  set(token: string | null): void {
    this.#value = token;
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [tokenStore] = useState(() => new AccessTokenStore());
  const [accessToken, setAccessTokenState] = useState<string | null>(null);
  const [user, setUser] = useState<UserResponse | null>(null);
  const [status, setStatus] = useState<SessionStatus>("bootstrapping");

  const [api] = useState(
    () =>
      new ApiClient({
        getAccessToken: () => tokenStore.get(),
        setAccessToken: (token) => {
          tokenStore.set(token);
          setAccessTokenState(token);
        },
        onSessionCleared: () => {
          setUser(null);
          setStatus("anonymous");
        },
      }),
  );

  const clearSession = useCallback(() => {
    tokenStore.set(null);
    setAccessTokenState(null);
    setUser(null);
    setStatus("anonymous");
  }, [tokenStore]);

  useEffect(() => {
    let cancelled = false;

    async function bootstrap() {
      try {
        const tokens = await api.webRefresh();
        if (!cancelled) {
          setUser(tokens.user);
          setStatus("authenticated");
        }
      } catch (error) {
        if (!cancelled) {
          clearSession();
          if (!(error instanceof ApiClientError)) {
            console.warn("Session bootstrap failed", error);
          }
        }
      }
    }

    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [api, clearSession]);

  const login = useCallback(
    async (credentials: LoginRequest) => {
      const tokens = await api.webLogin(credentials);
      setUser(tokens.user);
      setStatus("authenticated");
    },
    [api],
  );

  const logout = useCallback(async () => {
    try {
      await api.webLogout();
    } finally {
      clearSession();
    }
  }, [api, clearSession]);

  const refreshSession = useCallback(async () => {
    try {
      const tokens = await api.webRefresh();
      setUser(tokens.user);
      setStatus("authenticated");
      return true;
    } catch {
      clearSession();
      return false;
    }
  }, [api, clearSession]);

  const value = useMemo<SessionContextValue>(
    () => ({
      status,
      user,
      accessToken,
      api,
      login,
      logout,
      refreshSession,
    }),
    [status, user, accessToken, api, login, logout, refreshSession],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}
