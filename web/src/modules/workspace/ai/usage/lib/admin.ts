import { useCallback, useEffect } from "react";

import { axiosInstance } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";

import type { UsageScope } from "./api";

const API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

const USAGE_ADMIN_ROLES = new Set(["super-role", "role-template-ai-admin"]);

type SelfPermissions = {
  roles?: string[];
};

export function useUsageAdminAccess(): { isAdmin: boolean; isResolved: boolean } {
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
    isResolved: query.isSuccess || query.isError,
  };
}

export function useUsageScopeFallback(
  scope: UsageScope,
  isAdmin: boolean,
  isResolved: boolean,
  onScopeChange: (scope: UsageScope) => void,
  queryFailed: boolean,
) {
  useEffect(() => {
    if (!isResolved || scope !== "all") {
      return;
    }
    if (isAdmin && !queryFailed) {
      return;
    }
    onScopeChange("self");
    if (queryFailed) {
      toast.error("全站范围用量查询失败，已回退到本人范围。");
    }
  }, [isAdmin, isResolved, onScopeChange, queryFailed, scope]);
}

export function useScopeChange(update: (patch: { scope?: string }) => void) {
  return useCallback(
    (nextScope: UsageScope) => {
      update({ scope: nextScope === "all" ? "all" : undefined });
    },
    [update],
  );
}
