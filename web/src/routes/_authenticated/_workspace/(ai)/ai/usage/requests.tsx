import { createFileRoute } from "@tanstack/react-router";

import { AiUsageRequestsView } from "@/modules/workspace/ai/usage/requests";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/usage/requests")({
  component: AiUsageRequestsView,
});
