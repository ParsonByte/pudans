import { createFileRoute, redirect } from "@tanstack/react-router";

import { ConsoleFeaturePlaceholder } from "@/modules/workspace/console/feature-placeholder";
import { UI_PERMISSION_RISK_MANAGE, hasUiPermission } from "@/modules/workspace/lib/permissions";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/console/risk")({
  beforeLoad: ({ context }) => {
    if (!hasUiPermission(context.session.uiPermissions, UI_PERMISSION_RISK_MANAGE)) {
      throw redirect({ to: "/403", replace: true });
    }
  },
  component: ConsoleRiskRoute,
});

function ConsoleRiskRoute() {
  return <ConsoleFeaturePlaceholder title="风险" />;
}
