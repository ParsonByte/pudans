import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

async function fetchRoles() {
  return (await apiClient.role.listRole({ page: 1, size: 500 })).data.items ?? [];
}

export function useRoles() {
  return useQuery({
    queryKey: ["roles"],
    queryFn: fetchRoles,
    retry: false,
  });
}
