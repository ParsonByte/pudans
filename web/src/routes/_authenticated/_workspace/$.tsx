import { createFileRoute, useLocation } from "@tanstack/react-router";

import { WorkspaceMenuView } from "@/modules/workspace/menu-view";

export const Route = createFileRoute("/_authenticated/_workspace/$")({
  validateSearch: (search: Record<string, unknown>): { tab?: string } => {
    const tab = typeof search.tab === "string" ? search.tab : undefined;
    return tab ? { tab } : {};
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
