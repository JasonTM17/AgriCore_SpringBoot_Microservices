import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";

import { ApiClient } from "../api/client";
import { ApiClientError } from "../api/errors";
import type { LoginRequest, UserResponse } from "../api/types";

export type SessionStatus = "bootstrapping" | "authenticated" | "anonymous";

interface SessionContextValue {
  status: SessionStatus;
  user: UserResponse | null;
  accessToken: string | null;
  api: ApiClient;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  refreshSession: () => Promise<boolean>;
}

const SessionContext = createContext<SessionContextValue | null>(null);

function createSessionApi(
  accessTokenRef: { current: string | null },
  setAccessTokenState: (token: string | null) => void,
  onSessionCleared: () => void,
): ApiClient {
  return new ApiClient({
    getAccessToken: () => accessTokenRef.current,
    setAccessToken: (token) => {
      accessTokenRef.current = token;
      setAccessTokenState(token);
    },
    onSessionCleared,
  });
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const accessTokenRef = useRef<string | null>(null);
  const [accessToken, setAccessTokenState] = useState<string | null>(null);
  const [user, setUser] = useState<UserResponse | null>(null);
  const [status, setStatus] = useState<SessionStatus>("bootstrapping");

  const handleSessionCleared = useCallback(() => {
    setUser(null);
    setStatus("anonymous");
  }, []);

  // Lazily construct once; token reads happen only during request handlers.
  const [api] = useState(() =>
    createSessionApi(accessTokenRef, setAccessTokenState, () => {
      // Placeholder replaced after mount via ref below is unnecessary —
      // we pass handleSessionCleared through effect-safe wrapper.
      setUser(null);
      setStatus("anonymous");
    }),
  );

  // Keep cleared handler stable without recreating the client.
  useEffect(() => {
    // no-op: client already clears via setState closures above
    void handleSessionCleared;
  }, [handleSessionCleared]);

  useEffect(() => {
    let cancelled = false;

    async function bootstrap() {
      try {
        const tokens = await api.webRefresh();
        if (cancelled) {
          return;
        }
        setUser(tokens.user);
        setStatus("authenticated");
      } catch (error) {
        if (cancelled) {
          return;
        }
        accessTokenRef.current = null;
        setAccessTokenState(null);
        setUser(null);
        setStatus("anonymous");
        if (!(error instanceof ApiClientError)) {
          console.warn("Session bootstrap failed", error);
        }
      }
    }

    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [api]);

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
      setUser(null);
      setStatus("anonymous");
    }
  }, [api]);

  const refreshSession = useCallback(async () => {
    try {
      const tokens = await api.webRefresh();
      setUser(tokens.user);
      setStatus("authenticated");
      return true;
    } catch {
      accessTokenRef.current = null;
      setAccessTokenState(null);
      setUser(null);
      setStatus("anonymous");
      return false;
    }
  }, [api]);

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

export function useSession(): SessionContextValue {
  const ctx = useContext(SessionContext);
  if (!ctx) {
    throw new Error("useSession must be used within SessionProvider");
  }
  return ctx;
}
