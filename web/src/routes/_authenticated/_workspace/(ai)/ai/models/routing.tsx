import { createFileRoute } from "@tanstack/react-router";

import { AiRoutingView } from "@/modules/workspace/ai/routing";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/models/routing")({
  component: AiRoutingView,
});
