"use client";

import { useCallback, useMemo } from "react";

export type PermissionSubject = {
    roles?: string[];
    permissions?: string[];
};

export type PermissionRequirement =
    | string
    | string[]
    | {
          roles?: string[];
          permissions?: string[];
          mode?: "all" | "any";
      };

function normalizeRequirement(requirement: PermissionRequirement) {
    if (typeof requirement === "string") {
        return { permissions: [requirement] };
    }
    if (Array.isArray(requirement)) {
        return { permissions: requirement };
    }
    return requirement;
}

function matches(granted: string[] | undefined, required: string[] | undefined, mode: "all" | "any") {
    if (!required || required.length === 0) {
        return true;
    }
    if (!granted) {
        return false;
    }
    // “*” 表示拥有全部权限或角色。
    if (granted.includes("*")) {
        return true;
    }

    const check = (item: string) => granted.includes(item);
    return mode === "all" ? required.every(check) : required.some(check);
}

export function usePermission(subject: PermissionSubject, requirement: PermissionRequirement) {
    const roles = subject.roles;
    const permissions = subject.permissions;

    const allowed = useMemo(() => {
        const normalized = normalizeRequirement(requirement);
        const mode = normalized.mode ?? "all";

        return matches(roles, normalized.roles, mode) && matches(permissions, normalized.permissions, mode);
    }, [roles, permissions, requirement]);

    const can = useCallback((permission: string) => matches(permissions, [permission], "all"), [permissions]);
    const hasRole = useCallback((role: string) => matches(roles, [role], "all"), [roles]);

    return { allowed, can, hasRole };
}
