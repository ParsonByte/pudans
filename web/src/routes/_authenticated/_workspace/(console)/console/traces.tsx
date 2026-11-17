import { createFileRoute, redirect } from "@tanstack/react-router";

import { ConsoleFeaturePlaceholder } from "@/modules/workspace/console/feature-placeholder";
import { UI_PERMISSION_AUDIT_VIEW, hasUiPermission } from "@/modules/workspace/lib/permissions";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/console/traces")({
  beforeLoad: ({ context }) => {
    if (!hasUiPermission(context.session.uiPermissions, UI_PERMISSION_AUDIT_VIEW)) {
      throw redirect({ to: "/403", replace: true });
    }
  },
  component: ConsoleTracesRoute,
});

function ConsoleTracesRoute() {
  return <ConsoleFeaturePlaceholder title="链路追踪" />;
}
