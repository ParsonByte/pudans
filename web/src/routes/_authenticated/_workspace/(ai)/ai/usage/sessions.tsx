import { createFileRoute } from "@tanstack/react-router";

import { AiUsageSessionsView } from "@/modules/workspace/ai/usage/sessions";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/usage/sessions")({
  component: AiUsageSessionsView,
});
