import { createFileRoute } from "@tanstack/react-router";

import { TracesView } from "@/modules/workspace/console/traces";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/traces/")({
  component: TracesView,
});
