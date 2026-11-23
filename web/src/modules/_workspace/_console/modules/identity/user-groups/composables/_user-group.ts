import type { RoleRef, UserGroup } from "@pudans-dev/api-client";

import { BUILTIN_LABEL } from "../../composables/_role";

const USER_GROUP_API_VERSION = "pudans.tolim.cn/v1alpha1";
const USER_GROUP_KIND = "UserGroup";
const USER_GROUP_ROLE_API_GROUP = "pudans.tolim.cn";
const SUPER_ADMINS_USER_GROUP = "super-admins";

export const USER_GROUP_PERMISSIONS = {
    create: "user-groups:create",
    delete: "user-groups:delete",
    update: "user-groups:update",
    view: "user-groups:view",
} as const;

export type UserGroupProfileValues = {
    description?: string;
    displayName: string;
    name: string;
};

export function isBuiltinUserGroup(group: UserGroup): boolean {
    return group.metadata.labels?.[BUILTIN_LABEL] === "true";
}

export function isSuperAdminsUserGroup(group: UserGroup): boolean {
    return group.metadata.name === SUPER_ADMINS_USER_GROUP;
}

export function userGroupDisplayName(group: UserGroup): string {
    return group.displayName?.trim() || group.metadata.name;
}

export function userGroupDescription(group: UserGroup): string {
    return group.description?.trim() ?? "";
}

export function userGroupRoleNames(group: UserGroup): string[] {
    return Array.from(
        new Set(
            (group.roleRefs ?? [])
                .filter((roleRef) => roleRef.kind === "Role" && Boolean(roleRef.name))
                .map((roleRef) => roleRef.name as string),
        ),
    );
}

export function sortUserGroups(groups: UserGroup[]): UserGroup[] {
    return [...groups].sort((left, right) => {
        const builtinOrder = Number(isBuiltinUserGroup(right)) - Number(isBuiltinUserGroup(left));
        if (builtinOrder !== 0) {
            return builtinOrder;
        }

        return (
            userGroupDisplayName(left).localeCompare(userGroupDisplayName(right), "zh-Hans-CN", {
                sensitivity: "base",
            }) || left.metadata.name.localeCompare(right.metadata.name)
        );
    });
}

export function createUserGroupPayload(values: UserGroupProfileValues): UserGroup {
    return {
        apiVersion: USER_GROUP_API_VERSION,
        description: values.description?.trim() || undefined,
        displayName: values.displayName.trim(),
        kind: USER_GROUP_KIND,
        metadata: { name: values.name.trim() },
        roleRefs: [],
    };
}

export function buildUserGroupProfilePayload(group: UserGroup, values: UserGroupProfileValues): UserGroup {
    return {
        ...group,
        description: values.description?.trim() || undefined,
        displayName: values.displayName.trim(),
        metadata: { ...group.metadata, name: group.metadata.name },
    };
}

export function buildUserGroupRolePayload(group: UserGroup, roleNames: string[]): UserGroup {
    return {
        ...group,
        roleRefs: userGroupRoleRefs(roleNames),
    };
}

function userGroupRoleRefs(roleNames: string[]): RoleRef[] {
    return roleNames.map((name) => ({
        apiGroup: USER_GROUP_ROLE_API_GROUP,
        kind: "Role",
        name,
    }));
}
