import { createFileRoute } from "@tanstack/react-router";

import { ModelMarketView } from "@/modules/workspace/ai/market";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/models/market")({
  component: ModelMarketView,
});
