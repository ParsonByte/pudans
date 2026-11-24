import type { Role } from "@pudans-dev/api-client";
import { RobotIcon, SecuredIcon, StarIcon, SystemSettingIcon, UserIcon, UsergroupIcon } from "tdesign-icons-react";

import { isBuiltinRole } from "../../composables/_role";

type RoleIconProps = {
    role: Role;
    size?: number;
};

export function RoleIcon({ role, size = 16 }: RoleIconProps) {
    if (!isBuiltinRole(role)) {
        return <UsergroupIcon size={size} />;
    }

    switch (role.metadata.name) {
        case "super-role":
            return <StarIcon size={size} />;
        case "normal-role":
            return <UserIcon size={size} />;
        case "system-admin-role":
            return <SystemSettingIcon size={size} />;
        case "ai-operator-role":
            return <RobotIcon size={size} />;
        default:
            return <SecuredIcon size={size} />;
    }
}
