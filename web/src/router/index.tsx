import {
  createBrowserRouter,
  Navigate,
  Outlet,
  RouterProvider,
  type RouteObject,
} from "react-router-dom";
import { AuthLayout } from "../components/auth/components/auth-layout";
import { LoginView } from "../components/auth/modules/login";
import { RecoveryView } from "../components/auth/modules/recovery";
import { LegalView } from "../components/auth/modules/legal";
import { SignedInView } from "../components/auth/modules/signed-in";
import { SetupView } from "../components/setup";
import {
  ConsoleAuditView,
  ConsoleLayout,
  ConsoleOverviewView,
  ConsoleUsersView,
} from "../components/console";
import {
  UcAuthorizationsView,
  UcLayout,
  UcProfileView,
  UcSecurityView,
} from "../components/uc";
import {
  AppBootstrap,
  ForbiddenView,
  RedirectIfAuthenticated,
  RequirePermission,
  RequireSession,
  RootRedirect,
} from "./guards";

const routes: RouteObject[] = [
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
      {
        path: "/console",
        element: (
          <RequireSession access="console">
            <ConsoleLayout />
          </RequireSession>
        ),
        children: [
          {
            index: true,
            element: (
              <RequirePermission permissions={["dashboard:view"]}>
                <ConsoleOverviewView />
              </RequirePermission>
            ),
          },
          {
            path: "users",
            element: (
              <RequirePermission permissions={["users:manage"]}>
                <ConsoleUsersView />
              </RequirePermission>
            ),
          },
          {
            path: "audit",
            element: (
              <RequirePermission permissions={["audit:view"]}>
                <ConsoleAuditView />
              </RequirePermission>
            ),
          },
        ],
      },
      {
        path: "/uc",
        element: (
          <RequireSession>
            <UcLayout />
          </RequireSession>
        ),
        children: [
          { index: true, element: <UcProfileView /> },
          { path: "security", element: <UcSecurityView /> },
          { path: "authorizations", element: <UcAuthorizationsView /> },
        ],
      },
      { path: "/forbidden", element: <ForbiddenView /> },
      { path: "/", element: <RootRedirect /> },
      { path: "*", element: <Navigate to="/" replace /> },
    ],
  },
];

export const appRouter = createBrowserRouter(routes);

export function AppRouter() {
  return <RouterProvider router={appRouter} />;
}
