import { createFileRoute } from "@tanstack/react-router";

import { ConsoleSettingsView } from "@/modules/workspace/console/settings";
import { normalizeParameterGroupKey } from "@/modules/workspace/console/settings/lib/parameter-fields";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/settings")({
  validateSearch: (search: Record<string, unknown>) => ({
    tab: normalizeParameterGroupKey(search.tab),
  }),
  component: ConsoleSettingsRoute,
});

function ConsoleSettingsRoute() {
  const { tab } = Route.useSearch();
  const navigate = Route.useNavigate();

  return (
    <ConsoleSettingsView
      activeTab={tab}
      onTabChange={(nextTab) => {
        void navigate({ search: { tab: nextTab } });
      }}
    />
  );
}
