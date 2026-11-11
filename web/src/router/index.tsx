import { createBrowserRouter, RouterProvider, type RouteObject } from "react-router-dom";

export interface CreateAppRouterOptions {
  basename?: string;
}

export function createAppRouter(routes: RouteObject[], options: CreateAppRouterOptions = {}) {
  return createBrowserRouter(routes, {
    basename: options.basename,
  });
}

export interface AppRouterProps {
  router: ReturnType<typeof createAppRouter>;
}

export function AppRouter({ router }: AppRouterProps) {
  return <RouterProvider router={router} />;
}
