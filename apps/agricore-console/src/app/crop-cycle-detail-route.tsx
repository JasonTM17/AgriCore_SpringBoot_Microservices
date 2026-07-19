import { useParams } from "@tanstack/react-router";

import { RoleGate } from "./auth-gates";
import { CropCycleDetailPage } from "../features/crop-cycle/crop-cycle-detail-page";

export function CropCycleDetailRoute() {
  const { cycleId } = useParams({ from: "/authed/crop-cycles/$cycleId" });

  return (
    <RoleGate roles={["SYSTEM_ADMIN", "FARM_MANAGER", "AGRONOMIST", "FIELD_WORKER", "AUDITOR"]}>
      <CropCycleDetailPage cycleId={cycleId} />
    </RoleGate>
  );
}
