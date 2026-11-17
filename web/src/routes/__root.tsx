import { Outlet, createRootRoute } from "@tanstack/react-router";

import { NavigationProgress } from "@/components/navigation-progress";
import { Toaster } from "@/components/ui/sonner";
import { InternalServerError, NotFoundError } from "@/modules/errors";

export const Route = createRootRoute({
  component: RootRoute,
  notFoundComponent: NotFoundError,
  errorComponent: InternalServerError,
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
