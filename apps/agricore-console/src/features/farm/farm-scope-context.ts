import { createContext, useContext } from "react";

import type { FarmResponse } from "../../lib/api/types";

export interface FarmScopeValue {
  activeFarm: FarmResponse | null;
  selectFarm: (farm: FarmResponse | null) => void;
}

export const FarmScopeContext = createContext<FarmScopeValue | null>(null);

export function useFarmScope(): FarmScopeValue {
  const value = useContext(FarmScopeContext);
  if (!value) {
    throw new Error("useFarmScope must be used within FarmScopeProvider");
  }
  return value;
}
