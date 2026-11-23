"use client";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

const PERMISSION_CATALOG_QUERY_KEY = ["console", "permissions"] as const;

export function usePermissionCatalogQuery() {
    return useQuery({
        queryKey: PERMISSION_CATALOG_QUERY_KEY,
        queryFn: async () => (await apiClient.permissionCatalog.getPermissionCatalog()).data,
        refetchOnWindowFocus: false,
        staleTime: 60_000,
    });
}
