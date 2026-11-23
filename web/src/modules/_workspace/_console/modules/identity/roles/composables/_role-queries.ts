"use client";

import type { Role } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

const ROLE_LIST_QUERY_KEY = ["console", "roles", "all"] as const;
const ROLE_PAGE_SIZE = 500;

export function useRolesQuery() {
    return useQuery({
        queryKey: ROLE_LIST_QUERY_KEY,
        queryFn: async () => {
            const roles: Role[] = [];

            for (let page = 1; ; page += 1) {
                const result = (await apiClient.role.listRole({ page, size: ROLE_PAGE_SIZE })).data;
                roles.push(...(result.items ?? []));
                if (!result.hasNext) {
                    break;
                }
            }

            return roles.filter((role) => !role.metadata.deletionTimestamp);
        },
        refetchOnWindowFocus: false,
        staleTime: 60_000,
    });
}
