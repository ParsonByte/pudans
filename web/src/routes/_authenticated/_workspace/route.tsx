import { Outlet, createFileRoute, redirect } from "@tanstack/react-router";

import { recordNavigation } from "@/modules/workspace/lib/navigation-audit";
import { isPathAccessible } from "@/modules/workspace/lib/workspaces";

export const Route = createFileRoute("/_authenticated/_workspace")({
  beforeLoad: async ({ context, location }) => {
    if (!isPathAccessible(context.navigation, location.pathname)) {
      throw redirect({ to: "/403", replace: true });
    }
    await recordNavigation(location.pathname);
  },
  component: WorkspaceLayout,
});

function WorkspaceLayout() {
  return <Outlet />;
}
