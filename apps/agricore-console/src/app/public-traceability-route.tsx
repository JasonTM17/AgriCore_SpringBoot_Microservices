import { useParams } from "@tanstack/react-router";

import { PublicTraceabilityPage } from "../features/traceability/public-traceability-page";

export function PublicTraceabilityRoute() {
  const { code } = useParams({ from: "/public/traceability/$code" });
  return <PublicTraceabilityPage key={code} code={code} />;
}
