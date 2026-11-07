import type { RouteObject } from "react-router-dom";
import { RequireAuth } from "@web/router/guards";
import { UcLayout } from "../layouts/uc-layout";
import { ProfileView } from "../modules/profile/profile-view";
import { SecurityView } from "../modules/security/security-view";
import { AuthorizationsView } from "../modules/authorizations/authorizations-view";

export const routes: RouteObject[] = [
  {
    path: "/",
    element: (
      <RequireAuth access="any">
        <UcLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <ProfileView /> },
      { path: "security", element: <SecurityView /> },
      { path: "authorizations", element: <AuthorizationsView /> },
    ],
  },
];
