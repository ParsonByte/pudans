import { createFileRoute } from "@tanstack/react-router";

import { AiInstructionsView } from "@/modules/workspace/ai/library/instructions";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/library/instructions")({
  component: AiInstructionsView,
});
