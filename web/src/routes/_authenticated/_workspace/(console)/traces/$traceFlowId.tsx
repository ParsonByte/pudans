import { createFileRoute } from "@tanstack/react-router";

import { TraceDetailView } from "@/modules/workspace/console/traces/detail";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/traces/$traceFlowId")({
  component: TraceDetailRoute,
});

function TraceDetailRoute() {
  const { traceFlowId } = Route.useParams();

  return <TraceDetailView traceFlowId={traceFlowId} />;
}
