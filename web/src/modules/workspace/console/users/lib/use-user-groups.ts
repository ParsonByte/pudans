import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

export function useUserGroups() {
  return useQuery({
    queryKey: ["user-groups", "user-form"],
    queryFn: async () =>
      (await apiClient.userGroup.listUserGroup({ page: 1, size: 200 })).data.items,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}
