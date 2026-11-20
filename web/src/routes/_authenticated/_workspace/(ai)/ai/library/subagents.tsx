import { createFileRoute } from "@tanstack/react-router";

import { AiSubagentsView } from "@/modules/workspace/ai/library/subagents";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/library/subagents")({
  component: AiSubagentsView,
});
