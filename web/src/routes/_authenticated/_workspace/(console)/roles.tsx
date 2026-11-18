import { createFileRoute } from "@tanstack/react-router";

import { RolesView } from "@/modules/workspace/console/roles";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/roles")({
  component: RolesView,
});
