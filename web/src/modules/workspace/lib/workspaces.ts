import {
  Boxes,
  Fingerprint,
  History,
  LayoutDashboard,
  ScrollText,
  Settings2,
  ShieldAlert,
  ShieldCheck,
  Users,
  UsersRound,
  Waypoints,
} from "lucide-react";

import type { NavGroup, NavLink, Workspace } from "@/components/layout/types";

import {
  UI_PERMISSION_AUDIT_VIEW,
  UI_PERMISSION_AUTH_PROVIDERS_MANAGE,
  UI_PERMISSION_DASHBOARD_VIEW,
  UI_PERMISSION_RISK_MANAGE,
  UI_PERMISSION_ROLES_MANAGE,
  UI_PERMISSION_SETTINGS_MANAGE,
  UI_PERMISSION_USERS_MANAGE,
  hasUiPermission,
} from "./permissions";

type WorkspaceId = "index" | "console";

type WorkspaceDefinition = {
  id: WorkspaceId;
  name: string;
  plan: string;
  logo: Workspace["logo"];
  paths: readonly string[];
  isAvailable: (permissions?: readonly string[]) => boolean;
  resolveHomeUrl: (permissions?: readonly string[]) => string;
  buildNavGroups: (permissions?: readonly string[]) => NavGroup[];
};

const INDEX_HOME_URL = "/";
const CONSOLE_HOME_URL = "/console";
const CONSOLE_SETTINGS_URL = "/console/settings";
const CONSOLE_USERS_URL = "/console/users";
const CONSOLE_ROLES_URL = "/console/roles";
const CONSOLE_USER_GROUPS_URL = "/console/user-groups";
const CONSOLE_IDENTITY_PROVIDER_URL = "/console/identity-provider";
const CONSOLE_RISK_URL = "/console/risk";
const CONSOLE_AUDIT_URL = "/console/audit";
const CONSOLE_TRACES_URL = "/console/traces";
const CONSOLE_REPLAY_URL = "/console/replay";

const canViewConsole = (permissions?: readonly string[]) =>
  hasUiPermission(permissions, UI_PERMISSION_DASHBOARD_VIEW) ||
  hasUiPermission(permissions, UI_PERMISSION_SETTINGS_MANAGE) ||
  hasUiPermission(permissions, UI_PERMISSION_USERS_MANAGE) ||
  hasUiPermission(permissions, UI_PERMISSION_ROLES_MANAGE) ||
  hasUiPermission(permissions, UI_PERMISSION_AUTH_PROVIDERS_MANAGE) ||
  hasUiPermission(permissions, UI_PERMISSION_RISK_MANAGE) ||
  hasUiPermission(permissions, UI_PERMISSION_AUDIT_VIEW);

const INDEX_NAV_GROUPS: NavGroup[] = [
  {
    title: "默认工作区",
    items: [{ title: "总览", url: INDEX_HOME_URL, icon: Boxes }],
  },
];

const WORKSPACE_DEFINITIONS: readonly WorkspaceDefinition[] = [
  {
    id: "index",
    name: "默认",
    plan: "工作区",
    logo: Boxes,
    paths: [INDEX_HOME_URL],
    isAvailable: () => true,
    resolveHomeUrl: () => INDEX_HOME_URL,
    buildNavGroups: () => INDEX_NAV_GROUPS,
  },
  {
    id: "console",
    name: "终端管控",
    plan: "统一终端访问管控",
    logo: LayoutDashboard,
    paths: [CONSOLE_HOME_URL],
    isAvailable: canViewConsole,
    resolveHomeUrl: (permissions) => {
      if (hasUiPermission(permissions, UI_PERMISSION_DASHBOARD_VIEW)) {
        return CONSOLE_HOME_URL;
      }
      if (hasUiPermission(permissions, UI_PERMISSION_SETTINGS_MANAGE)) {
        return CONSOLE_SETTINGS_URL;
      }
      if (hasUiPermission(permissions, UI_PERMISSION_USERS_MANAGE)) {
        return CONSOLE_USERS_URL;
      }
      if (hasUiPermission(permissions, UI_PERMISSION_ROLES_MANAGE)) {
        return CONSOLE_ROLES_URL;
      }
      if (hasUiPermission(permissions, UI_PERMISSION_AUTH_PROVIDERS_MANAGE)) {
        return CONSOLE_IDENTITY_PROVIDER_URL;
      }
      if (hasUiPermission(permissions, UI_PERMISSION_RISK_MANAGE)) {
        return CONSOLE_RISK_URL;
      }
      if (hasUiPermission(permissions, UI_PERMISSION_AUDIT_VIEW)) {
        return CONSOLE_AUDIT_URL;
      }
      return CONSOLE_HOME_URL;
    },
    buildNavGroups: (permissions) => {
      const groups: NavGroup[] = [];
      const generalItems: NavLink[] = [];

      if (hasUiPermission(permissions, UI_PERMISSION_DASHBOARD_VIEW)) {
        generalItems.push({ title: "面板首页", url: CONSOLE_HOME_URL, icon: LayoutDashboard });
      }
      if (hasUiPermission(permissions, UI_PERMISSION_SETTINGS_MANAGE)) {
        generalItems.push({ title: "参数配置", url: CONSOLE_SETTINGS_URL, icon: Settings2 });
      }
      if (hasUiPermission(permissions, UI_PERMISSION_AUTH_PROVIDERS_MANAGE)) {
        generalItems.push({
          title: "身份提供商",
          url: CONSOLE_IDENTITY_PROVIDER_URL,
          icon: Fingerprint,
        });
      }
      if (generalItems.length > 0) {
        groups.push({ title: "通用", items: generalItems });
      }

      const controlItems: NavLink[] = [];

      if (hasUiPermission(permissions, UI_PERMISSION_USERS_MANAGE)) {
        controlItems.push({ title: "用户", url: CONSOLE_USERS_URL, icon: Users });
      }
      if (hasUiPermission(permissions, UI_PERMISSION_ROLES_MANAGE)) {
        controlItems.push({ title: "角色", url: CONSOLE_ROLES_URL, icon: ShieldCheck });
        controlItems.push({
          title: "用户组",
          url: CONSOLE_USER_GROUPS_URL,
          icon: UsersRound,
        });
      }
      if (hasUiPermission(permissions, UI_PERMISSION_RISK_MANAGE)) {
        controlItems.push({ title: "风险", url: CONSOLE_RISK_URL, icon: ShieldAlert });
      }
      if (hasUiPermission(permissions, UI_PERMISSION_AUDIT_VIEW)) {
        controlItems.push({ title: "审计", url: CONSOLE_AUDIT_URL, icon: ScrollText });
        controlItems.push({ title: "链路追踪", url: CONSOLE_TRACES_URL, icon: Waypoints });
        controlItems.push({ title: "回溯", url: CONSOLE_REPLAY_URL, icon: History });
      }
      if (controlItems.length > 0) {
        groups.push({ title: "控制", items: controlItems });
      }

      return groups;
    },
  },
];

export function buildWorkspaces(permissions?: readonly string[]): Workspace[] {
  return WORKSPACE_DEFINITIONS.filter((definition) => definition.isAvailable(permissions)).map(
    (definition) => ({
      id: definition.id,
      name: definition.name,
      plan: definition.plan,
      logo: definition.logo,
      homeUrl: definition.resolveHomeUrl(permissions),
      paths: [...definition.paths],
    }),
  );
}

export function buildWorkspaceNavGroups(
  workspaceId: string,
  permissions?: readonly string[],
): NavGroup[] {
  return (
    WORKSPACE_DEFINITIONS.find((definition) => definition.id === workspaceId)?.buildNavGroups(
      permissions,
    ) ?? []
  );
}

export function resolveActiveWorkspace(
  workspaces: readonly Workspace[],
  pathname: string,
): Workspace | undefined {
  const currentPath = normalizePath(pathname);

  return (
    workspaces.find((workspace) =>
      workspace.paths.some((path) => {
        const workspacePath = normalizePath(path);

        return currentPath === workspacePath || currentPath.startsWith(`${workspacePath}/`);
      }),
    ) ?? workspaces[0]
  );
}

function normalizePath(path: string): string {
  return path.replace(/\/+$/, "") || "/";
}
