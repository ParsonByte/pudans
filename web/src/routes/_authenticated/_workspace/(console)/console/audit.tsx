import { createFileRoute, redirect } from "@tanstack/react-router";

import { ConsoleFeaturePlaceholder } from "@/modules/workspace/console/feature-placeholder";
import { UI_PERMISSION_AUDIT_VIEW, hasUiPermission } from "@/modules/workspace/lib/permissions";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/console/audit")({
  beforeLoad: ({ context }) => {
    if (!hasUiPermission(context.session.uiPermissions, UI_PERMISSION_AUDIT_VIEW)) {
      throw redirect({ to: "/403", replace: true });
    }
  },
  component: ConsoleAuditRoute,
});

function ConsoleAuditRoute() {
  return <ConsoleFeaturePlaceholder title="审计" />;
}
