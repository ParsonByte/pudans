import { useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { resolveApiError } from "@/lib/api-error";
import {
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

const PROFILE_QUERY_KEY = ["account-profile"];

const ERROR_MESSAGES = {
  notFound: "用户不存在。",
  conflict: "保存失败，请稍后重试。",
};

export function AccountProfileView() {
  const queryClient = useQueryClient();
  const profileQuery = useQuery({
    queryKey: PROFILE_QUERY_KEY,
    queryFn: async () => (await apiClient.user.getCurrentUser()).data,
  });
  const user = profileQuery.data;
  const [bioDraft, setBioDraft] = useState<string | null>(null);
  const bio = bioDraft ?? user?.spec.bio ?? "";

  const refresh = () => queryClient.invalidateQueries({ queryKey: PROFILE_QUERY_KEY });

  const saveMutation = useMutation({
    mutationFn: async () =>
      (await apiClient.user.updateCurrentUser({ updateProfileRequest: { bio } })).data,
    onSuccess: () => {
      toast.success("个人简介已保存。");
      setBioDraft(null);
      void refresh();
    },
    onError: (error) => toast.error(resolveApiError(error, ERROR_MESSAGES)),
  });

  const initials = (user?.spec.displayName ?? user?.metadata.name ?? "?").slice(0, 2).toUpperCase();

  return (
    <ConsolePage title="个人设置">
      <ConsoleSection>
        <ConsoleSectionHeader
          title="个人资料"
          description="姓名、邮箱、手机号与头像由飞书同步，个人简介可自行维护。"
        />
        <div className="space-y-5 p-5">
          <div className="flex flex-wrap items-center gap-4">
            <Avatar className="size-16">
              {user?.spec.avatar ? <AvatarImage src={user.spec.avatar} alt="" /> : null}
              <AvatarFallback className="text-lg">{initials}</AvatarFallback>
            </Avatar>
            <div className="space-y-1">
              <p className="text-sm font-medium">{user?.spec.displayName ?? "—"}</p>
              <p className="text-xs text-muted-foreground">头像与资料来自飞书，绑定后自动同步。</p>
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="display-name">显示名称</Label>
              <Input id="display-name" value={user?.spec.displayName ?? ""} disabled />
            </div>
            <div className="space-y-2">
              <Label htmlFor="profile-email">邮箱</Label>
              <Input id="profile-email" value={user?.spec.email ?? ""} disabled />
            </div>
            <div className="space-y-2">
              <Label htmlFor="profile-phone">手机号</Label>
              <Input id="profile-phone" value={user?.spec.phone ?? ""} disabled />
            </div>
            <div className="space-y-2">
              <Label htmlFor="profile-bio">个人简介</Label>
              <Textarea
                id="profile-bio"
                value={bio}
                onChange={(event) => setBioDraft(event.target.value)}
                placeholder="介绍一下自己"
                rows={3}
              />
            </div>
          </div>

          <div className="flex justify-end">
            <Button onClick={() => saveMutation.mutate()} disabled={saveMutation.isPending}>
              {saveMutation.isPending ? <Loader2 className="animate-spin" /> : null}
              保存修改
            </Button>
          </div>
        </div>
      </ConsoleSection>

      <ConsoleSection>
        <ConsoleSectionHeader title="账号信息" description="账号标识与注册时间不可修改。" />
        <dl className="grid gap-4 p-5 md:grid-cols-3">
          <div>
            <dt className="text-xs text-muted-foreground">用户名</dt>
            <dd className="mt-1 font-mono text-sm">{user?.metadata.name ?? "—"}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">用户 ID</dt>
            <dd className="mt-1 font-mono text-sm">{user?.spec.uid ?? "—"}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">注册时间</dt>
            <dd className="mt-1 text-sm">{formatUserDateTime(user?.spec.registeredAt)}</dd>
          </div>
        </dl>
      </ConsoleSection>
    </ConsolePage>
  );
}
