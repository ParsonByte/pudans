import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

export const USER_GROUPS_QUERY_KEY = ["user-groups"] as const;

type UseUserGroupsOptions = {
  enabled?: boolean;
};

export function useUserGroups(options: UseUserGroupsOptions = {}) {
  return useQuery({
    queryKey: USER_GROUPS_QUERY_KEY,
    queryFn: async () =>
      (await apiClient.userGroup.listUserGroup({ page: 1, size: 500 })).data.items ?? [],
    enabled: options.enabled ?? true,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}
