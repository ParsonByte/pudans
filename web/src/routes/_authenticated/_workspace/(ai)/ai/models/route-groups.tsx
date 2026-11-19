import { createFileRoute } from "@tanstack/react-router";

import { AiRouteGroupsView } from "@/modules/workspace/ai/route-groups";

export const Route = createFileRoute("/_authenticated/_workspace/(ai)/ai/models/route-groups")({
  component: AiRouteGroupsView,
});
