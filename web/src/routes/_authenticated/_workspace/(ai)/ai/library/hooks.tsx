import { createFileRoute } from "@tanstack/react-router";

import { AiHooksView } from "@/modules/workspace/ai/library/hooks";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/library/hooks")({
  component: AiHooksView,
});
