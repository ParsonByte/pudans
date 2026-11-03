import type { RouteObject } from "react-router-dom";
import { Navigate } from "react-router-dom";
import { AuthLayout } from "../layouts/auth-layout";
import { LoginView } from "../modules/login/login-view";
import { RecoveryView } from "../modules/recovery/recovery-view";
import { LegalView } from "../modules/legal/legal-view";

export const routes: RouteObject[] = [
  {
    path: "/",
    element: <AuthLayout />,
    children: [
      { index: true, element: <LoginView /> },
      { path: "login", element: <Navigate to="/" replace /> },
      { path: "recovery", element: <RecoveryView /> },
      { path: "legal", element: <LegalView /> },
    ],
  },
];
