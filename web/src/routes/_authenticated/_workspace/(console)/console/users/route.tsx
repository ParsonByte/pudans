import { Outlet, createFileRoute, redirect } from "@tanstack/react-router";

import { UI_PERMISSION_USERS_MANAGE, hasUiPermission } from "@/modules/workspace/lib/permissions";

export const Route = createFileRoute("/_authenticated/_workspace/(console)/console/users")({
  beforeLoad: ({ context }) => {
    if (!hasUiPermission(context.session.uiPermissions, UI_PERMISSION_USERS_MANAGE)) {
      throw redirect({ to: "/403", replace: true });
    }
  },
  component: UsersLayout,
});

function UsersLayout() {
  return <Outlet />;
}
