import { createFileRoute, redirect } from "@tanstack/react-router";

import { ConsoleOverviewView } from "@/modules/workspace/console";
import { UI_PERMISSION_DASHBOARD_VIEW, hasUiPermission } from "@/modules/workspace/lib/permissions";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/console/")({
  beforeLoad: ({ context }) => {
    if (!hasUiPermission(context.session.uiPermissions, UI_PERMISSION_DASHBOARD_VIEW)) {
      throw redirect({ to: "/403", replace: true });
    }
  },
  component: ConsoleOverviewView,
});
