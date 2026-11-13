import { createBrowserRouter, Navigate, Outlet, RouterProvider } from "react-router-dom";
import { AuthLayout } from "../../../src/components/auth/components/auth-layout";
import { LoginView } from "../../../src/components/auth/modules/login";
import { RecoveryView } from "../../../src/components/auth/modules/recovery";
import { LegalView } from "../../../src/components/auth/modules/legal";
import { SignedInView } from "../../../src/components/auth/modules/signed-in";
import { SetupView } from "../../../src/components/setup";
import {
  AppBootstrap,
  ForbiddenView,
  NotFoundView,
  RedirectIfAuthenticated,
  RootRedirect,
} from "./guards";

/**
 * Basic auth routes only. Console and UC are independent multi-page apps with
 * their own dynamic module routes; nothing product-specific is registered in
 * the auth router.
 */
const routes = [
  {
    element: (
      <AppBootstrap>
        <Outlet />
      </AppBootstrap>
    ),
    children: [
      {
        path: "/auth",
        element: <AuthLayout />,
        children: [
          {
            index: true,
            element: (
              <RedirectIfAuthenticated>
                <LoginView />
              </RedirectIfAuthenticated>
            ),
          },
          {
            path: "recovery",
            element: (
              <RedirectIfAuthenticated>
                <RecoveryView />
              </RedirectIfAuthenticated>
            ),
          },
          { path: "legal", element: <LegalView /> },
          { path: "signed-in", element: <SignedInView /> },
          { path: "done", element: <Navigate to="/auth/signed-in" replace /> },
          { path: "login", element: <Navigate to="/auth" replace /> },
        ],
      },
      {
        path: "/setup",
        element: <AuthLayout />,
        children: [{ index: true, element: <SetupView /> }],
      },
      { path: "/forbidden", element: <ForbiddenView /> },
      { path: "/", element: <RootRedirect /> },
      { path: "*", element: <NotFoundView /> },
    ],
  },
];

export const appRouter = createBrowserRouter(routes);

export function AppRouter() {
  return <RouterProvider router={appRouter} />;
}
