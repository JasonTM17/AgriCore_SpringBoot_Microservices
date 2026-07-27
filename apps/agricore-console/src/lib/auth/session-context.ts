import { createContext, useContext } from "react";

import type { ApiClient } from "../api/client";
import type { LoginRequest, UserResponse } from "../api/types";

export type SessionStatus = "bootstrapping" | "authenticated" | "anonymous";

export interface SessionContextValue {
  status: SessionStatus;
  user: UserResponse | null;
  accessToken: string | null;
  api: ApiClient;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  refreshSession: () => Promise<boolean>;
}

export const SessionContext = createContext<SessionContextValue | null>(null);

export function useSession(): SessionContextValue {
  const context = useContext(SessionContext);
  if (!context) {
    throw new Error("useSession must be used within SessionProvider");
  }
  return context;
}
