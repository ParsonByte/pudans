import type { Role, RoleRef, UserGroup, UserGroupBinding } from "@pudans-dev/api-client";

import {
  compareRoles,
  isBuiltinRole,
  roleDisplayName,
  visibleRoles,
} from "@/modules/workspace/console/roles/lib/role-model";

export const USER_GROUP_API_VERSION = "pudans.tolim.cn/v1alpha1";

export const USER_GROUP_KIND = "UserGroup";

const SUPER_ADMINS_GROUP = "super-admins";

const SYSTEM_ADMINS_GROUP = "system-admins";

const NORMAL_USERS_GROUP = "normal-users";

const USER_GROUP_BUILTIN_LABEL = "pudans.tolim.cn/builtin";

const ROLE_KIND = "Role";

const USER_KIND = "User";

const API_GROUP = "pudans.tolim.cn";

export type UserGroupRoleOption = {
  name: string;
  label: string;
  builtin: boolean;
  locked: boolean;
};

export function isBuiltinUserGroup(group: UserGroup): boolean {
  return (
    group.metadata.labels?.[USER_GROUP_BUILTIN_LABEL] === "true" ||
    group.metadata.name === SUPER_ADMINS_GROUP ||
    group.metadata.name === SYSTEM_ADMINS_GROUP ||
    group.metadata.name === NORMAL_USERS_GROUP
  );
}

export function isSuperAdminsGroup(group: UserGroup): boolean {
  return group.metadata.name === SUPER_ADMINS_GROUP;
}

export function isRoleAdjustableBuiltinGroup(group: UserGroup): boolean {
  return group.metadata.name === SYSTEM_ADMINS_GROUP || group.metadata.name === NORMAL_USERS_GROUP;
}

export function userGroupDisplayName(group: UserGroup): string {
  return group.displayName || group.metadata.name;
}

export function userGroupRoleNames(group: UserGroup): string[] {
  return (group.roleRefs ?? [])
    .filter((roleRef) => roleRef.kind === ROLE_KIND && Boolean(roleRef.name))
    .map((roleRef) => roleRef.name as string);
}

export function userGroupRoleRefs(roleNames: readonly string[]): RoleRef[] {
  return roleNames.map((name) => ({
    kind: ROLE_KIND,
    name,
    apiGroup: API_GROUP,
  }));
}

export function userGroupRoleOptions(
  roles: readonly Role[],
  lockedRoleNames: readonly string[],
): UserGroupRoleOption[] {
  const lockedNames = new Set(lockedRoleNames);
  const options = visibleRoles(roles)
    .filter((role) => !isBuiltinRole(role) || lockedNames.has(role.metadata.name))
    .sort(compareRoles)
    .map((role) => ({
      name: role.metadata.name,
      label: roleDisplayName(role),
      builtin: isBuiltinRole(role),
      locked: lockedNames.has(role.metadata.name),
    }));
  for (const lockedName of lockedNames) {
    if (!options.some((option) => option.name === lockedName)) {
      options.push({
        name: lockedName,
        label: lockedName,
        builtin: true,
        locked: true,
      });
    }
  }
  return options;
}

export function userGroupSubject(groupName: string): string {
  return `${API_GROUP}/${USER_GROUP_KIND}/${groupName}`;
}

export function membershipBindingUsernames(
  bindings: readonly UserGroupBinding[],
  groupName: string,
): string[] {
  const names = new Set<string>();
  for (const binding of bindings) {
    const roleRef = binding.roleRef;
    if (roleRef?.kind !== USER_GROUP_KIND || roleRef.name !== groupName) {
      continue;
    }
    for (const subject of binding.subjects ?? []) {
      if (subject.kind === USER_KIND && subject.apiGroup === API_GROUP && subject.name) {
        names.add(subject.name);
      }
    }
  }
  return [...names];
}

export function roleBindingNames(
  bindings: readonly UserGroupBinding[],
  groupName: string,
): string[] {
  const names = new Set<string>();
  for (const binding of bindings) {
    const roleRef = binding.roleRef;
    if (roleRef?.kind !== ROLE_KIND || !roleRef.name) {
      continue;
    }
    const grantedToGroup = (binding.subjects ?? []).some(
      (subject) =>
        subject.kind === USER_GROUP_KIND &&
        subject.apiGroup === API_GROUP &&
        subject.name === groupName,
    );
    if (grantedToGroup) {
      names.add(roleRef.name);
    }
  }
  return [...names];
}
