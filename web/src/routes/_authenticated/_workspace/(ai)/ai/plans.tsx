import { createFileRoute } from "@tanstack/react-router";

import { AiPlansView } from "@/modules/workspace/ai/plans";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/plans")({
  component: AiPlansView,
});
