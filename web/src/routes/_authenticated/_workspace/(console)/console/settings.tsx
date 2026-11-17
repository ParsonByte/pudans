import { createFileRoute, redirect } from "@tanstack/react-router";

import { ConsoleSettingsView } from "@/modules/workspace/console";
import { normalizeParameterGroupKey } from "@/modules/workspace/console/settings/lib/parameter-fields";
import {
  UI_PERMISSION_SETTINGS_MANAGE,
  hasUiPermission,
} from "@/modules/workspace/lib/permissions";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/console/settings")({
  validateSearch: (search: Record<string, unknown>) => ({
    tab: normalizeParameterGroupKey(search.tab),
  }),
  beforeLoad: ({ context }) => {
    if (!hasUiPermission(context.session.uiPermissions, UI_PERMISSION_SETTINGS_MANAGE)) {
      throw redirect({ to: "/403", replace: true });
    }
  },
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
