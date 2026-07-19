import { useParams } from "@tanstack/react-router";

import { HarvestReceiptPage } from "../features/harvest/harvest-receipt-page";
import { HARVEST_VIEW_ROLES } from "../features/harvest/harvest-roles";
import { RoleGate } from "./auth-gates";

export function HarvestReceiptRoute() {
  const { harvestId } = useParams({ from: "/authed/harvests/$harvestId" });

  return (
    <RoleGate roles={HARVEST_VIEW_ROLES}>
      <HarvestReceiptPage harvestId={harvestId} />
    </RoleGate>
  );
}
