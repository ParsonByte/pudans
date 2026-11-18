import { createFileRoute, useLocation } from "@tanstack/react-router";

import { WorkspaceMenuView } from "@/modules/workspace/menu-view";

export const Route = createFileRoute("/_authenticated/_workspace/$")({
  validateSearch: (
    search: Record<string, unknown>,
  ): { tab?: string; from?: string; to?: string } => {
    const result: { tab?: string; from?: string; to?: string } = {};
    if (typeof search.tab === "string") {
      result.tab = search.tab;
    }
    if (typeof search.from === "string") {
      result.from = search.from;
    }
    if (typeof search.to === "string") {
      result.to = search.to;
    }
    return result;
  },
  component: WorkspaceMenuRoute,
});

function WorkspaceMenuRoute() {
  const { navigation } = Route.useRouteContext();
  const pathname = useLocation({ select: (location) => location.pathname });
  const { tab } = Route.useSearch();
  const navigate = Route.useNavigate();

  return (
    <WorkspaceMenuView
      navigation={navigation}
      pathname={pathname}
      tab={tab}
      onTabChange={(nextTab) => {
        void navigate({ search: { tab: nextTab } });
      }}
    />
  );
}
