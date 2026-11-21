import type { Role, RoleBinding, Subject } from "@pudans-dev/api-client";

import { compareRoles, isBuiltinRole, roleDisplayName, visibleRoles } from "./role-model";

export const RBAC_API_GROUP = "pudans.tolim.cn";

const RBAC_USER_KIND = "User";

const RBAC_USER_GROUP_KIND = "UserGroup";

const TEMP_ROLE_SUPER_ROLE_NAME = "super-role";

export const TEMP_ROLE_PENDING = "Pending";

export const TEMP_ROLE_ACTIVE = "Active";

export const TEMP_ROLE_EXPIRED = "Expired";

export const TEMP_ROLE_REVOKED = "Revoked";

export const TEMP_ROLE_STATUS_OPTIONS = [
  { value: TEMP_ROLE_PENDING, label: "待审批" },
  { value: TEMP_ROLE_ACTIVE, label: "生效中" },
  { value: TEMP_ROLE_EXPIRED, label: "已过期" },
  { value: TEMP_ROLE_REVOKED, label: "已撤销" },
] as const;

type BindingSubjectKind = "User" | "UserGroup";

export type BindingSubjectOption = {
  key: string;
  kind: BindingSubjectKind;
  name: string;
  label: string;
  hint?: string;
};

type BadgeVariant = "default" | "secondary" | "destructive" | "outline";

export function subjectOptionKey(kind: BindingSubjectKind, name: string): string {
  return `${kind}:${name}`;
}

export function parseSubjectOptionKey(
  key: string,
): { kind: BindingSubjectKind; name: string } | null {
  const separator = key.indexOf(":");
  if (separator <= 0) {
    return null;
  }
  const kind = key.slice(0, separator);
  const name = key.slice(separator + 1);
  if ((kind !== RBAC_USER_KIND && kind !== RBAC_USER_GROUP_KIND) || !name) {
    return null;
  }
  return { kind, name };
}

export function roleBindingRoleName(binding: RoleBinding): string {
  return binding.roleRef?.kind === "Role" ? (binding.roleRef.name ?? "") : "";
}

export function roleBindingSubjectKeys(binding: RoleBinding): string[] {
  return (binding.subjects ?? [])
    .filter(
      (subject): subject is Subject & { kind: BindingSubjectKind; name: string } =>
        Boolean(subject.name) &&
        (subject.apiGroup === RBAC_API_GROUP || !subject.apiGroup) &&
        (subject.kind === RBAC_USER_KIND || subject.kind === RBAC_USER_GROUP_KIND),
    )
    .map((subject) => subjectOptionKey(subject.kind, subject.name));
}

export function bindableRoleOptions(roles: readonly Role[]): Role[] {
  return visibleRoles(roles).sort(compareRoles);
}

function tempRoleOptions(roles: readonly Role[]): Role[] {
  return bindableRoleOptions(roles).filter(
    (role) => role.metadata.name !== TEMP_ROLE_SUPER_ROLE_NAME,
  );
}

export function tempRoleStatusLabel(status: string | undefined): string {
  return (
    TEMP_ROLE_STATUS_OPTIONS.find((option) => option.value === status)?.label ?? status ?? "未知"
  );
}

export function tempRoleStatusVariant(status: string | undefined): BadgeVariant {
  switch (status) {
    case TEMP_ROLE_ACTIVE:
      return "default";
    case TEMP_ROLE_REVOKED:
      return "destructive";
    case TEMP_ROLE_EXPIRED:
      return "outline";
    default:
      return "secondary";
  }
}

export function tempRoleBindingRoleNames(roleNames: readonly string[] | undefined): string[] {
  return [...new Set(roleNames ?? [])].filter(Boolean);
}

export function tempRoleOptionList(roles: readonly Role[]): {
  name: string;
  label: string;
  builtin: boolean;
}[] {
  return tempRoleOptions(roles).map((role) => ({
    name: role.metadata.name,
    label: roleDisplayName(role),
    builtin: isBuiltinRole(role),
  }));
}
