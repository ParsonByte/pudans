const UI_PERMISSION_ALL = "*";

export const UI_PERMISSION_DASHBOARD_VIEW = "dashboard:view";

export const UI_PERMISSION_SETTINGS_MANAGE = "settings:manage";

export const UI_PERMISSION_USERS_MANAGE = "users:manage";

export const UI_PERMISSION_ROLES_MANAGE = "roles:manage";

export function hasUiPermission(
  permissions: readonly string[] | undefined,
  required: string,
): boolean {
  return Boolean(permissions?.includes(UI_PERMISSION_ALL) || permissions?.includes(required));
}
