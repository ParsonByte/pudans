import type { RouteObject } from "react-router-dom";
import { RequireAuth } from "@web/router/guards";
import { ConsoleLayout } from "../layouts/console-layout";
import { ConsolePlaceholder } from "../components/console-placeholder";
import { OverviewView } from "../modules/overview/overview-view";
import { UserListView } from "../modules/users/user-list-view";
import { RoleListView } from "../modules/roles/role-list-view";
import { AuditListView } from "../modules/audit/audit-list-view";
import { SettingsView } from "../modules/settings/settings-view";

export const routes: RouteObject[] = [
  {
    path: "/",
    element: (
      <RequireAuth access="console">
        <ConsoleLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <OverviewView /> },
      { path: "settings", element: <SettingsView /> },
      { path: "users", element: <UserListView /> },
      { path: "roles", element: <RoleListView /> },
      { path: "usergroups", element: <ConsolePlaceholder titleKey="usergroupsTitle" /> },
      { path: "plugins", element: <ConsolePlaceholder titleKey="pluginsTitle" /> },
      { path: "integrations", element: <ConsolePlaceholder titleKey="integrationsTitle" /> },
      { path: "audit", element: <AuditListView /> },
      { path: "risk", element: <ConsolePlaceholder titleKey="riskTitle" /> },
    ],
  },
];
