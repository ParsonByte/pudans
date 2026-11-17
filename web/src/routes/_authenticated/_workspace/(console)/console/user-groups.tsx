import { createFileRoute, redirect } from "@tanstack/react-router";

import { ConsoleFeaturePlaceholder } from "@/modules/workspace/console/feature-placeholder";
import { UI_PERMISSION_ROLES_MANAGE, hasUiPermission } from "@/modules/workspace/lib/permissions";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/console/user-groups")({
  beforeLoad: ({ context }) => {
    if (!hasUiPermission(context.session.uiPermissions, UI_PERMISSION_ROLES_MANAGE)) {
      throw redirect({ to: "/403", replace: true });
    }
  },
  component: ConsoleUserGroupsRoute,
});

function ConsoleUserGroupsRoute() {
  return <ConsoleFeaturePlaceholder title="用户组" />;
}
