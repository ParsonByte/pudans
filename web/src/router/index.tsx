import { createBrowserRouter, Navigate, Outlet, RouterProvider } from "react-router-dom";
import { AuthLayout } from "../components/auth/components/auth-layout";
import { LoginView } from "../components/auth/modules/login";
import { RecoveryView } from "../components/auth/modules/recovery";
import { LegalView } from "../components/auth/modules/legal";
import { SignedInView } from "../components/auth/modules/signed-in";
import { SetupView } from "../components/setup";
import {
  AppBootstrap,
  ForbiddenView,
  NotFoundView,
  RedirectIfAuthenticated,
  RequireSession,
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
          {
            path: "done",
            element: (
              <RequireSession>
                <SignedInView />
              </RequireSession>
            ),
          },
          { path: "login", element: <Navigate to="/auth/" replace /> },
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
