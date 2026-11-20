import { createFileRoute } from "@tanstack/react-router";

import { AiUsageOverviewView } from "@/modules/workspace/ai/usage/overview";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/usage/overview")({
  component: AiUsageOverviewView,
});
