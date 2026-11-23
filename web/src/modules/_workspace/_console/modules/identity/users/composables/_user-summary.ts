"use client";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

export function useUserSummaryQuery(enabled: boolean) {
    return useQuery({
        enabled,
        queryKey: ["console", "users", "summary"],
        queryFn: async () => {
            const [active, disabled] = await Promise.all([
                apiClient.user.listUsers({ page: 1, size: 1, status: "active" }),
                apiClient.user.listUsers({ page: 1, size: 1, status: "disabled" }),
            ]);
            const activeCount = active.data.total ?? 0;
            const disabledCount = disabled.data.total ?? 0;

            return {
                active: activeCount,
                disabled: disabledCount,
                total: activeCount + disabledCount,
            };
        },
        refetchInterval: 60_000,
        staleTime: 30_000,
    });
}
