import { createFileRoute } from "@tanstack/react-router";

import { AiConsolePage } from "@/modules/workspace/ai";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/library/instructions")({
  component: AiConsolePage,
});
