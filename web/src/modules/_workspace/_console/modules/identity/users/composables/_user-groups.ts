"use client";

import { useMemo } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

export type UserGroupOption = {
    label: string;
    value: string;
};

export function useUserGroupOptions() {
    const query = useQuery({
        queryKey: ["console", "user-groups", "options"],
        queryFn: async () => (await apiClient.userGroup.listUserGroup({ page: 1, size: 200 })).data,
    });
    const options = useMemo<UserGroupOption[]>(
        () =>
            (query.data?.items ?? []).map((group) => ({
                label: group.displayName,
                value: group.metadata.name,
            })),
        [query.data?.items],
    );

    return { options, query };
}
