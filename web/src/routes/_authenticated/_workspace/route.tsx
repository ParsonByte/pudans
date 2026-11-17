import { Outlet, createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/_workspace")({
  component: WorkspaceLayout,
});

function WorkspaceLayout() {
  return <Outlet />;
}
