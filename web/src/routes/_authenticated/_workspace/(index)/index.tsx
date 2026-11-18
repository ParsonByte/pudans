import { createFileRoute } from "@tanstack/react-router";

import { WorkspaceIndexView } from "@/modules/workspace/index/view";

export const Route = createFileRoute("/_authenticated/_workspace/(index)/")({
  component: WorkspaceIndexRoute,
});

function WorkspaceIndexRoute() {
  const { navigation } = Route.useRouteContext();

  return <WorkspaceIndexView navigation={navigation} />;
}
