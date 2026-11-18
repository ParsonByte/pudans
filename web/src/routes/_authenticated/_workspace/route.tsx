import { Outlet, createFileRoute, redirect } from "@tanstack/react-router";

import { isPathAccessible } from "@/modules/workspace/lib/workspaces";

export const Route = createFileRoute("/_authenticated/_workspace")({
  beforeLoad: ({ context, location }) => {
    if (!isPathAccessible(context.navigation, location.pathname)) {
      throw redirect({ to: "/403", replace: true });
    }
  },
  component: WorkspaceLayout,
});

function WorkspaceLayout() {
  return <Outlet />;
}
