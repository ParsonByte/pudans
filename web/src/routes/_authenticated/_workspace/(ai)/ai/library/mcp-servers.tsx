import { createFileRoute } from "@tanstack/react-router";

import { AiMcpServersView } from "@/modules/workspace/ai/library/mcp-servers";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/library/mcp-servers")({
  component: AiMcpServersView,
});
