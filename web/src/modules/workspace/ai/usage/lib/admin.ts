import { axiosInstance } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

const API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

const USAGE_ADMIN_ROLES = new Set(["super-role", "system-admin-role", "role-template-ai-admin"]);

type SelfPermissions = {
  roles?: string[];
};

export function useUsageAdminAccess(): { isAdmin: boolean } {
  const query = useQuery({
    queryKey: ["users", "me", "permissions"],
    queryFn: async () => {
      const response = await axiosInstance.get<SelfPermissions>(
        `${API_PREFIX}/users/-/permissions`,
      );
      return response.data;
    },
    retry: false,
    staleTime: 5 * 60_000,
  });

  return {
    isAdmin: (query.data?.roles ?? []).some((role) => USAGE_ADMIN_ROLES.has(role)),
  };
}
