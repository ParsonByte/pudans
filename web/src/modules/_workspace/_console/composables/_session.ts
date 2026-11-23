"use client";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

export function useConsoleSession() {
    return useQuery({
        queryKey: ["console", "session"],
        queryFn: async () => (await apiClient.login.getLoginSession()).data,
    });
}
