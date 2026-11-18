import { axiosInstance } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

const API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

export const MY_UI_PERMISSIONS_QUERY_KEY = ["users", "me", "ui-permissions"] as const;

export function useMyUiPermissions() {
  return useQuery({
    queryKey: MY_UI_PERMISSIONS_QUERY_KEY,
    queryFn: async () => {
      const response = await axiosInstance.get<string[]>(`${API_PREFIX}/users/-/ui-permissions`);
      return Array.isArray(response.data) ? response.data : [];
    },
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}
