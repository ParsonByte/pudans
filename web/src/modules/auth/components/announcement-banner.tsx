"use client";

import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Megaphone, X } from "lucide-react";
import { toast } from "sonner";

import { Alert, AlertAction, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { fetchParameterGroup } from "@/modules/workspace/console/settings/lib/parameter-api";

const ANNOUNCEMENT_STATE_QUERY_KEY = ["account-announcement-state"] as const;
const ANNOUNCEMENT_SITE_QUERY_KEY = ["account-announcement-site"] as const;

export function AnnouncementBanner() {
  const queryClient = useQueryClient();
  const stateQuery = useQuery({
    queryKey: ANNOUNCEMENT_STATE_QUERY_KEY,
    queryFn: async () => (await apiClient.user.getMyAnnouncementState()).data,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
  const state = stateQuery.data;
  const visible = Boolean(state?.version) && state?.dismissed !== true;

  const siteQuery = useQuery({
    queryKey: ANNOUNCEMENT_SITE_QUERY_KEY,
    queryFn: () => fetchParameterGroup("site"),
    enabled: visible,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
  const announcement = siteQuery.data?.siteAnnouncement;
  const content = typeof announcement === "string" ? announcement.trim() : "";

  const dismissMutation = useMutation({
    mutationFn: async () => (await apiClient.user.dismissMyAnnouncement()).data,
    onSuccess: (next) => {
      queryClient.setQueryData(ANNOUNCEMENT_STATE_QUERY_KEY, next);
      toast.success("公告已关闭。");
    },
    onError: () => toast.error("关闭公告失败，请稍后重试。"),
  });

  if (!visible || !content) {
    return null;
  }

  return (
    <div className="px-4 pt-4">
      <Alert>
        <Megaphone />
        <AlertTitle>站点公告</AlertTitle>
        <AlertDescription className="whitespace-pre-wrap">{content}</AlertDescription>
        <AlertAction>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label="关闭公告"
            title="关闭公告"
            disabled={dismissMutation.isPending}
            onClick={() => dismissMutation.mutate()}
          >
            <X />
          </Button>
        </AlertAction>
      </Alert>
    </div>
  );
}
