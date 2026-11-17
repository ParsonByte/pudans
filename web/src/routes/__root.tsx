import { Outlet, createRootRoute } from "@tanstack/react-router";

import { NavigationProgress } from "@/components/navigation-progress";
import { Toaster } from "@/components/ui/sonner";

export const Route = createRootRoute({
  component: RootRoute,
});

function RootRoute() {
  return (
    <>
      <NavigationProgress />
      <Outlet />
      <Toaster position="top-center" richColors />
    </>
  );
}
