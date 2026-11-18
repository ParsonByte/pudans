import { createFileRoute } from "@tanstack/react-router";

import { ReplayView } from "@/modules/workspace/console/replay";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/replay")({
  component: ReplayView,
});
