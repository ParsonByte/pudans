import { createFileRoute } from "@tanstack/react-router";

import { AgentRuntimeView } from "@/modules/workspace/ai/agent-runtime";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/agent-runtime")({
  component: AgentRuntimeView,
});
