import { Outlet, createRootRoute } from "@tanstack/react-router";

import { NavigationProgress } from "@/components/navigation-progress";

export const Route = createRootRoute({
  component: RootRoute,
});

function RootRoute() {
  return (
    <>
      <NavigationProgress />
      <Outlet />
    </>
  );
}
