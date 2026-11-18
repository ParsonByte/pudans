import { createFileRoute } from "@tanstack/react-router";

import { JourneyDetailView } from "@/modules/workspace/console/traces/journey-detail";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/traces/user/$uid")({
  validateSearch: (search: Record<string, unknown>): { from?: string; to?: string } => {
    const result: { from?: string; to?: string } = {};
    if (typeof search.from === "string") {
      result.from = search.from;
    }
    if (typeof search.to === "string") {
      result.to = search.to;
    }
    return result;
  },
  component: JourneyDetailRoute,
});

function JourneyDetailRoute() {
  const { uid } = Route.useParams();

  return <JourneyDetailView uid={uid} />;
}
