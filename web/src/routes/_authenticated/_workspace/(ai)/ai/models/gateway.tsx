import { createFileRoute } from "@tanstack/react-router";

import { AiGatewayView } from "@/modules/workspace/ai/gateway";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/models/gateway")({
  component: AiGatewayView,
});
