/**
 * UI permissions and the route policy table shared by the auth landing logic
 * and the console/uc dynamic route definitions. The auth app validates a
 * redirect_uri against the same table before sending the browser to a module
 * route, so a redirect can never bypass a route permission gate.
 */
export const UI_PERMISSIONS = {
  ALL: "*",
  DASHBOARD_VIEW: "dashboard:view",
  USERS_MANAGE: "users:manage",
  ROLES_MANAGE: "roles:manage",
  AUDIT_VIEW: "audit:view",
  RISK_MANAGE: "risk:manage",
  WEBHOOKS_MANAGE: "webhooks:manage",
  SETTINGS_MANAGE: "settings:manage",
  AUTH_PROVIDERS_MANAGE: "auth-providers:manage",
  PLUGINS_MANAGE: "plugins:manage",
  OPS_VIEW: "ops:view",
} as const;

export interface RoutePolicy {
  path: string;
  permissions: readonly string[];
}

export const APP_ROUTE_POLICIES: readonly RoutePolicy[] = [
  { path: "/console", permissions: [] },
  { path: "/console/dashboard", permissions: [UI_PERMISSIONS.DASHBOARD_VIEW] },
  { path: "/console/users", permissions: [UI_PERMISSIONS.USERS_MANAGE] },
  { path: "/console/audit", permissions: [UI_PERMISSIONS.AUDIT_VIEW] },
  { path: "/uc", permissions: [] },
  { path: "/uc/profile", permissions: [] },
  { path: "/uc/security", permissions: [] },
  { path: "/uc/authorizations", permissions: [] },
];

export type PermissionCheck = (permission: string) => boolean;

function normalizePath(path: string): string {
  if (!path) {
    return "/";
  }
  const [pathname] = path.split(/[?#]/);
  return pathname.length > 1 ? pathname.replace(/\/+$/, "") : pathname;
}

export function policyForPath(path: string): RoutePolicy | undefined {
  const pathname = normalizePath(path);
  return [...APP_ROUTE_POLICIES]
    .sort((left, right) => right.path.length - left.path.length)
    .find(
      (policy) => pathname === policy.path || pathname.startsWith(policy.path + "/"),
    );
}

export function isRoutePermitted(path: string, hasPermission: PermissionCheck): boolean {
  const policy = policyForPath(path);
  if (!policy || policy.permissions.length === 0) {
    return true;
  }
  return policy.permissions.some((permission) => hasPermission(permission));
}

export function firstPermittedPath(
  paths: readonly string[],
  hasPermission: PermissionCheck,
): string | undefined {
  return paths.find((path) => isRoutePermitted(path, hasPermission));
}
