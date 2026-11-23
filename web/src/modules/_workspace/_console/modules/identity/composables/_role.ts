import type { Role } from "@pudans-dev/api-client";

export const ROLE_DISPLAY_NAME_ANNOTATION = "rbac.authorization.pudans.tolim.cn/display-name";
export const ROLE_TEMPLATE_LABEL = "pudans.tolim.cn/role-template";

export function roleDisplayName(role: Role): string {
    return role.metadata.annotations?.[ROLE_DISPLAY_NAME_ANNOTATION] ?? role.metadata.name;
}
