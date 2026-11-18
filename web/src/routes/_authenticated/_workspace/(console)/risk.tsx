import { createFileRoute } from "@tanstack/react-router";

import { RiskView } from "@/modules/workspace/console/risk";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/risk")({
  component: RiskView,
});
