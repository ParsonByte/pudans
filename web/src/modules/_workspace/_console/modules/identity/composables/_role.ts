import type { Role } from "@pudans-dev/api-client";

const ROLE_BUILTIN_LABEL = "pudans.tolim.cn/builtin";
export const ROLE_DEPENDENCIES_ANNOTATION = "rbac.authorization.pudans.tolim.cn/dependencies";
export const ROLE_DESCRIPTION_ANNOTATION = "rbac.authorization.pudans.tolim.cn/description";
export const ROLE_DISPLAY_NAME_ANNOTATION = "rbac.authorization.pudans.tolim.cn/display-name";
export const ROLE_TEMPLATE_LABEL = "pudans.tolim.cn/role-template";
export const ROLE_UI_PERMISSIONS_ANNOTATION = "rbac.authorization.pudans.tolim.cn/ui-permissions";

export const ROLE_PERMISSIONS = {
    create: "roles:create",
    delete: "roles:delete",
    update: "roles:update",
    view: "roles:view",
} as const;

export function roleDisplayName(role: Role): string {
    return role.metadata.annotations?.[ROLE_DISPLAY_NAME_ANNOTATION] ?? role.metadata.name;
}

export function roleDescription(role: Role): string {
    return role.metadata.annotations?.[ROLE_DESCRIPTION_ANNOTATION] ?? "";
}

export function isBuiltinRole(role: Role): boolean {
    return role.metadata.labels?.[ROLE_BUILTIN_LABEL] === "true";
}

export function roleDependencies(role: Role): string[] {
    return parseRoleList(role.metadata.annotations?.[ROLE_DEPENDENCIES_ANNOTATION]);
}

export function roleDirectPermissions(role: Role): string[] {
    return parseRoleList(role.metadata.annotations?.[ROLE_UI_PERMISSIONS_ANNOTATION]);
}

function parseRoleList(value?: string): string[] {
    if (!value) {
        return [];
    }

    try {
        const parsed: unknown = JSON.parse(value);

        if (!Array.isArray(parsed)) {
            return [];
        }

        return parsed.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
    } catch {
        return [];
    }
}
