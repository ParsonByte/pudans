import { createFileRoute } from "@tanstack/react-router";

import { AiUsageContextView } from "@/modules/workspace/ai/usage/context";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/usage/context")({
  component: AiUsageContextView,
});
