"use client";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

const LISTED_AUTH_PROVIDERS_QUERY_KEY = ["console", "auth-providers", "listed"] as const;

export function useListedAuthProvidersQuery(enabled = true) {
    return useQuery({
        enabled,
        queryKey: LISTED_AUTH_PROVIDERS_QUERY_KEY,
        queryFn: async () => (await apiClient.authProvider.listAuthProviders()).data,
    });
}
