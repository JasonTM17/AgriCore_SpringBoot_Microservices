import { useQuery } from "@tanstack/react-query";
import { useCallback, useMemo, useState, type ReactNode } from "react";

import type { FarmResponse } from "../../lib/api/types";
import { useSession } from "../../lib/auth/session";
import { listFarms, type FarmListParams } from "./farm-api";
import { farmQueryKeys } from "./farm-query-keys";
import { FarmScopeContext } from "./farm-scope-context";

const defaultFarmParams: FarmListParams = {
  page: 0,
  size: 20,
  sort: "code,asc",
};

/**
 * Holds navigation context only. Every service remains responsible for
 * authorizing the selected farm and its resources at the API boundary.
 */
export function FarmScopeProvider({ children }: { children: ReactNode }) {
  const { api, user } = useSession();
  const subject = user?.id ?? "unauthenticated";
  const [selectedFarm, setSelectedFarm] = useState<FarmResponse | null>(null);
  const farmsQuery = useQuery({
    queryKey: farmQueryKeys.list(subject, defaultFarmParams),
    queryFn: ({ signal }) => listFarms(api, defaultFarmParams, signal),
    enabled: user !== null,
  });
  const activeFarm =
    farmsQuery.data?.totalElements === 0
      ? null
      : (selectedFarm ?? farmsQuery.data?.content[0] ?? null);
  const selectFarm = useCallback((farm: FarmResponse | null) => {
    setSelectedFarm(farm);
  }, []);
  const value = useMemo(() => ({ activeFarm, selectFarm }), [activeFarm, selectFarm]);

  return <FarmScopeContext.Provider value={value}>{children}</FarmScopeContext.Provider>;
}
