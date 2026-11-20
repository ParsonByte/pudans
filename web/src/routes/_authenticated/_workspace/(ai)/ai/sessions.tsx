import { createFileRoute } from "@tanstack/react-router";

import { SessionsView } from "@/modules/workspace/ai/sessions";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/sessions")({
  component: SessionsView,
});
