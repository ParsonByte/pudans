import { createBrowserRouter, Navigate, RouterProvider, type RouteObject } from "react-router-dom";
import { AUTH_BASENAME, AUTH_ROUTES } from "../constants";
import { AuthLayout } from "../components/auth/components/auth-layout";
import { LoginView } from "../components/auth/modules/login";
import { RecoveryView } from "../components/auth/modules/recovery";
import { LegalView } from "../components/auth/modules/legal";
import { SignedInView } from "../components/auth/modules/signed-in";

const routes: RouteObject[] = [
  {
    path: "/",
    element: <AuthLayout />,
    children: [
      { index: true, element: <LoginView /> },
      { path: AUTH_ROUTES.recovery.slice(1), element: <RecoveryView /> },
      { path: AUTH_ROUTES.legal.slice(1), element: <LegalView /> },
      { path: AUTH_ROUTES.signedIn.slice(1), element: <SignedInView /> },
      { path: "login", element: <Navigate to="/" replace /> },
      { path: "*", element: <Navigate to="/" replace /> },
    ],
  },
];

export const authRouter = createBrowserRouter(routes, { basename: AUTH_BASENAME });

export function AuthRouter() {
  return <RouterProvider router={authRouter} />;
}
