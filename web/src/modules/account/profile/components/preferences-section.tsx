"use client";

import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { resolveApiError } from "@/lib/api-error";
import {
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import {
  ConsoleBlockSkeleton,
  ConsoleState,
} from "@/modules/workspace/console/components/console-state";

import {
  PROFILE_PREFERENCES_QUERY_KEY,
  PROFILE_PREFERENCE_GROUP,
  type ProfilePreferences,
  useProfilePreferences,
} from "../lib/preferences";

const ERROR_MESSAGES = {
  notFound: "偏好设置不存在。",
  conflict: "偏好设置保存失败，请稍后重试。",
};

type PreferenceRowProps = {
  id: string;
  label: string;
  description: string;
  checked: boolean;
  disabled: boolean;
  onCheckedChange: (checked: boolean) => void;
};

function PreferenceRow({
  id,
  label,
  description,
  checked,
  disabled,
  onCheckedChange,
}: PreferenceRowProps) {
  return (
    <div className="flex items-start justify-between gap-4 px-5 py-4">
      <div className="space-y-1">
        <Label htmlFor={id}>{label}</Label>
        <p className="text-xs leading-5 text-muted-foreground">{description}</p>
      </div>
      <Switch id={id} checked={checked} disabled={disabled} onCheckedChange={onCheckedChange} />
    </div>
  );
}

export function ProfilePreferencesSection() {
  const queryClient = useQueryClient();
  const preferencesQuery = useProfilePreferences();
  const preferences = preferencesQuery.data;

  const mutation = useMutation({
    mutationFn: async (next: ProfilePreferences) => {
      await apiClient.userPreference.updateMyPreference({
        group: PROFILE_PREFERENCE_GROUP,
        body: JSON.stringify(next),
      });
      return next;
    },
    onMutate: (next) => {
      const previous = queryClient.getQueryData<ProfilePreferences>(PROFILE_PREFERENCES_QUERY_KEY);
      queryClient.setQueryData(PROFILE_PREFERENCES_QUERY_KEY, next);
      return { previous };
    },
    onError: (error, _next, context) => {
      if (context?.previous) {
        queryClient.setQueryData(PROFILE_PREFERENCES_QUERY_KEY, context.previous);
      }
      toast.error(resolveApiError(error, ERROR_MESSAGES));
    },
    onSuccess: () => toast.success("偏好设置已保存。"),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: PROFILE_PREFERENCES_QUERY_KEY });
    },
  });

  const update = (patch: Partial<ProfilePreferences>) => {
    if (!preferences) {
      return;
    }
    mutation.mutate({ ...preferences, ...patch });
  };

  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="偏好设置"
        description="偏好设置保存在账号中，登录其他设备时自动同步。"
      />
      {preferencesQuery.isPending ? (
        <ConsoleBlockSkeleton rows={2} className="grid gap-2 p-4" itemClassName="h-12" />
      ) : preferencesQuery.isError || !preferences ? (
        <ConsoleState
          variant="error"
          title="无法读取偏好设置。"
          description="偏好设置暂时不可用，请重试。"
          onRetry={() => void preferencesQuery.refetch()}
        />
      ) : (
        <div className="divide-y">
          <PreferenceRow
            id="profile-bio-preview"
            label="显示简介预览"
            description="在个人简介下方以 Markdown 预览当前内容。"
            checked={preferences.bioPreview}
            disabled={mutation.isPending}
            onCheckedChange={(checked) => update({ bioPreview: checked })}
          />
          <PreferenceRow
            id="profile-account-info"
            label="显示账号信息"
            description="显示用户名、用户 ID 与注册时间等只读账号信息。"
            checked={preferences.showAccountInfo}
            disabled={mutation.isPending}
            onCheckedChange={(checked) => update({ showAccountInfo: checked })}
          />
        </div>
      )}
    </ConsoleSection>
  );
}
