import { useParams } from "@tanstack/react-router";

import { HarvestReceiptPage } from "../features/harvest/harvest-receipt-page";
import { RoleGate } from "./auth-gates";

export function HarvestReceiptRoute() {
  const { harvestId } = useParams({ from: "/authed/harvests/$harvestId" });

  return (
    <RoleGate roles={["SYSTEM_ADMIN", "FARM_MANAGER", "AGRONOMIST", "FIELD_WORKER", "WAREHOUSE_MANAGER", "AUDITOR"]}>
      <HarvestReceiptPage harvestId={harvestId} />
    </RoleGate>
  );
}
