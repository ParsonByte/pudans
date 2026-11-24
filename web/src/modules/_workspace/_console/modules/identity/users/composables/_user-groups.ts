"use client";

import { useMemo } from "react";

import { sortUserGroups, userGroupDisplayName } from "../../user-groups/composables/_user-group";
import { useUserGroupsQuery } from "../../user-groups/composables/_user-group-queries";

export type UserGroupOption = {
    label: string;
    value: string;
};

export function useUserGroupOptions() {
    const query = useUserGroupsQuery();
    const groups = useMemo(() => sortUserGroups(query.data ?? []), [query.data]);
    const options = useMemo<UserGroupOption[]>(
        () =>
            groups.map((group) => ({
                label: userGroupDisplayName(group),
                value: group.metadata.name,
            })),
        [groups],
    );

    return { groups, options, query };
}
