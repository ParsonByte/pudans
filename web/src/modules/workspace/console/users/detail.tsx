import { useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import {
  ArrowLeft,
  Ban,
  CheckCircle2,
  Fingerprint,
  KeyRound,
  Link2,
  Pencil,
  RefreshCw,
  Trash2,
  Users,
} from "lucide-react";
import { toast } from "sonner";

import { IconFeishu } from "@/assets/custom/icon-feishu";
import { ActivityBarChart } from "@/components/activity-bar-chart";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { InfoRow } from "@/components/info-row";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { useAllowedOperations, useMenuPath } from "@/modules/workspace/lib/permissions";

import { ChangeGroupDialog } from "./components/change-group-dialog";
import { EditUserDialog } from "./components/edit-user-dialog";
import { ResetPasswordDialog } from "./components/reset-password-dialog";
import { UserAvatar } from "./components/user-avatar";
import { fetchUserActivity, fetchUserConnections, resolveUserApiError } from "./lib/api";
import { formatUserDateTime } from "./lib/format";

const PROVIDER_LABELS: Record<string, string> = {
  feishu: "飞书",
  passkey: "通行密钥",
};

type UserDetailViewProps = {
  name: string;
};

export function UserDetailView({ name }: UserDetailViewProps) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const allowedOperations = useAllowedOperations();
  const usersPath = useMenuPath("users");
  const canUpdate = allowedOperations.has("update");
  const canResetPassword = allowedOperations.has("reset-password");
  const canChangeGroup = allowedOperations.has("change-group");
  const canChangeStatus = allowedOperations.has("toggle-status");
  const canDelete = allowedOperations.has("delete");
  const [editOpen, setEditOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [groupOpen, setGroupOpen] = useState(false);
  const [statusConfirmOpen, setStatusConfirmOpen] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [groupRemoveConfirmOpen, setGroupRemoveConfirmOpen] = useState(false);

  const userQuery = useQuery({
    queryKey: ["user", name],
    queryFn: async () => (await apiClient.user.getUser({ name })).data,
    retry: false,
  });
  const groupQuery = useQuery({
    queryKey: ["user-group", name],
    queryFn: async () => (await apiClient.user.getUserGroup({ name })).data,
    retry: false,
  });
  const connectionsQuery = useQuery({
    queryKey: ["user-connections", name],
    queryFn: () => fetchUserConnections(name),
    retry: false,
  });
  const activityQuery = useQuery({
    queryKey: ["user-activity", name],
    queryFn: () => fetchUserActivity(name, 14),
    retry: false,
  });
  const loginProvidersQuery = useQuery({
    queryKey: ["login-providers"],
    queryFn: async () => (await apiClient.login.listLoginProviders()).data,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });

  const user = userQuery.data;
  const spec = user?.spec;
  const username = user?.metadata?.name ?? name;
  const displayName = spec?.displayName || username;
  const disabled = Boolean(spec?.disabled);
  const connections = connectionsQuery.data ?? [];
  const loginProviders = loginProvidersQuery.data ?? [];
  const activityPoints = activityQuery.data ?? [];
  const totalActivity = activityPoints.reduce((sum, point) => sum + (point.count ?? 0), 0);
  const hasActivity = activityPoints.some((point) => (point.count ?? 0) > 0);

  const invalidateUser = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["user", name] }),
      queryClient.invalidateQueries({ queryKey: ["user-group", name] }),
      queryClient.invalidateQueries({ queryKey: ["users"] }),
    ]);
  };

  const statusMutation = useMutation({
    mutationFn: async () => {
      if (disabled) {
        await apiClient.user.enableUser({ username });
      } else {
        await apiClient.user.disableUser({ username });
      }
    },
    onSuccess: async () => {
      await invalidateUser();
      setStatusConfirmOpen(false);
      toast.success(disabled ? "用户已启用。" : "用户已禁用。");
    },
    onError: (error) => {
      setStatusConfirmOpen(false);
      toast.error(resolveUserApiError(error));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async () => {
      await apiClient.user.deleteUser({ name: username });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["users"] });
      toast.success("用户已删除。");
      if (usersPath) {
        void navigate({ to: "/$", params: { _splat: usersPath } });
      }
    },
    onError: (error) => toast.error(resolveUserApiError(error)),
  });

  const removeGroupMutation = useMutation({
    mutationFn: async () => {
      await apiClient.user.removeUserGroup({ name: username });
    },
    onSuccess: async () => {
      await invalidateUser();
      setGroupRemoveConfirmOpen(false);
      toast.success("用户组已移除。");
    },
    onError: (error) => {
      setGroupRemoveConfirmOpen(false);
      toast.error(resolveUserApiError(error));
    },
  });

  if (userQuery.isPending) {
    return (
      <ConsolePage title="用户详情">
        <div className="space-y-4">
          <Skeleton className="h-10 w-40" />
          <div className="grid gap-4 xl:grid-cols-3">
            <Skeleton className="h-64 rounded-xl xl:col-span-2" />
            <Skeleton className="h-64 rounded-xl" />
            <Skeleton className="h-48 rounded-xl" />
            <Skeleton className="h-48 rounded-xl xl:col-span-2" />
          </div>
        </div>
      </ConsolePage>
    );
  }

  if (userQuery.isError || !user) {
    return (
      <ConsolePage title="用户详情">
        <ConsoleSection className="flex min-h-64 flex-col items-center justify-center gap-4 p-8 text-center">
          <Users className="size-8 text-muted-foreground" />
          <div className="space-y-1">
            <h2 className="text-sm font-semibold">无法加载用户</h2>
            <p className="text-xs text-muted-foreground">{resolveUserApiError(userQuery.error)}</p>
          </div>
          <div className="flex gap-2">
            {usersPath ? (
              <Button variant="outline" size="sm" asChild>
                <Link to="/$" params={{ _splat: usersPath }}>
                  <ArrowLeft />
                  返回用户列表
                </Link>
              </Button>
            ) : null}
            <Button variant="outline" size="sm" onClick={() => void userQuery.refetch()}>
              <RefreshCw />
              重试
            </Button>
          </div>
        </ConsoleSection>
      </ConsolePage>
    );
  }

  const group = groupQuery.data;

  return (
    <ConsolePage title={displayName}>
      <div className="flex flex-wrap items-center gap-2">
        {usersPath ? (
          <Button variant="ghost" size="sm" asChild>
            <Link to="/$" params={{ _splat: usersPath }}>
              <ArrowLeft />
              返回用户列表
            </Link>
          </Button>
        ) : null}
        <div className="ms-auto flex flex-wrap items-center gap-2">
          {canUpdate ? (
            <Button variant="outline" size="sm" onClick={() => setEditOpen(true)}>
              <Pencil />
              编辑简介
            </Button>
          ) : null}
          {canResetPassword ? (
            <Button variant="outline" size="sm" onClick={() => setPasswordOpen(true)}>
              <KeyRound />
              重置密码
            </Button>
          ) : null}
          {canChangeStatus ? (
            <Button
              variant={disabled ? "default" : "outline"}
              size="sm"
              onClick={() => setStatusConfirmOpen(true)}
              disabled={statusMutation.isPending}
            >
              {disabled ? <CheckCircle2 /> : <Ban />}
              {disabled ? "启用用户" : "禁用用户"}
            </Button>
          ) : null}
          {canDelete ? (
            <Button
              variant="destructive"
              size="sm"
              onClick={() => setDeleteConfirmOpen(true)}
              disabled={deleteMutation.isPending}
            >
              <Trash2 />
              删除用户
            </Button>
          ) : null}
        </div>
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        <ConsoleSection className="xl:col-span-2">
          <ConsoleSectionHeader
            title="用户资料"
            description="姓名、邮箱、手机号和头像由飞书同步。"
            action={
              <Badge variant={disabled ? "destructive" : "secondary"}>
                {disabled ? "已禁用" : "正常"}
              </Badge>
            }
          />
          <div className="flex flex-col gap-6 p-5 sm:flex-row">
            <UserAvatar
              name={username}
              displayName={displayName}
              avatar={spec?.avatar}
              className="size-20 shrink-0 text-xl"
            />
            <div className="grid flex-1 gap-x-6 gap-y-4 sm:grid-cols-2">
              <InfoRow
                label="唯一 ID"
                value={<span className="font-mono text-xs">{spec?.uid || "—"}</span>}
              />
              <InfoRow label="用户名" value={`@${username}`} />
              <InfoRow label="邮箱" value={spec?.email || "—"} />
              <InfoRow label="手机号" value={spec?.phone || "—"} />
              <InfoRow label="注册时间" value={formatUserDateTime(spec?.registeredAt)} />
            </div>
          </div>
          {spec?.bio ? (
            <div className="border-t px-5 py-4">
              <p className="mb-2 text-xs font-medium text-muted-foreground">个人简介</p>
              <p className="text-sm whitespace-pre-wrap">{spec.bio}</p>
            </div>
          ) : null}
        </ConsoleSection>

        <div className="grid content-start gap-4">
          <ConsoleSection>
            <ConsoleSectionHeader
              title="用户组"
              description="用户组决定用户继承的角色。"
              action={
                group && canChangeGroup ? (
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm" onClick={() => setGroupOpen(true)}>
                      调整
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setGroupRemoveConfirmOpen(true)}
                      disabled={removeGroupMutation.isPending}
                    >
                      移除
                    </Button>
                  </div>
                ) : null
              }
            />
            {groupQuery.isPending ? (
              <div className="p-5">
                <Skeleton className="h-16 w-full" />
              </div>
            ) : group ? (
              <div className="space-y-3 p-5">
                <div>
                  <p className="text-sm font-medium">{group.displayName || group.metadata.name}</p>
                  <p className="text-xs text-muted-foreground">@{group.metadata.name}</p>
                </div>
                {group.description ? (
                  <p className="text-sm text-muted-foreground">{group.description}</p>
                ) : null}
              </div>
            ) : (
              <ConsoleEmpty>该用户没有绑定用户组。</ConsoleEmpty>
            )}
          </ConsoleSection>

          <ConsoleSection>
            <ConsoleSectionHeader title="身份绑定" description="已关联的飞书或其他身份提供商。" />
            {connectionsQuery.isPending ? (
              <div className="space-y-3 p-5">
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
              </div>
            ) : connectionsQuery.isError ? (
              <ConsoleEmpty>{resolveUserApiError(connectionsQuery.error)}</ConsoleEmpty>
            ) : connections.length === 0 ? (
              <ConsoleEmpty>尚未绑定飞书或其他身份提供商。</ConsoleEmpty>
            ) : (
              <div className="divide-y">
                {connections.map((connection) => {
                  const provider = loginProviders.find(
                    (item) => item.name === connection.registrationId,
                  );

                  return (
                    <div
                      key={
                        connection.name ??
                        `${connection.registrationId}-${connection.providerUserId}`
                      }
                      className="flex items-center gap-3 px-5 py-3"
                    >
                      <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-muted">
                        <ConnectionIcon
                          registrationId={connection.registrationId}
                          logo={provider?.logo}
                        />
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium">
                          {providerLabel(connection.registrationId, provider?.displayName)}
                        </p>
                        <p className="truncate font-mono text-xs text-muted-foreground">
                          {connection.providerUserId || "—"}
                        </p>
                      </div>
                      {connection.createdAt ? (
                        <span
                          className="shrink-0 text-xs text-muted-foreground"
                          title={`绑定于 ${formatUserDateTime(connection.createdAt)}`}
                        >
                          {formatUserDateTime(connection.createdAt)}
                        </span>
                      ) : null}
                    </div>
                  );
                })}
              </div>
            )}
          </ConsoleSection>
        </div>

        <ConsoleSection className="xl:col-span-3">
          <ConsoleSectionHeader
            title="活动"
            description="最近 14 天操作次数"
            action={
              !activityQuery.isPending && !activityQuery.isError && hasActivity ? (
                <span className="text-xs text-muted-foreground">
                  共 {totalActivity.toLocaleString("zh-CN")} 次操作
                </span>
              ) : null
            }
          />
          {activityQuery.isPending ? (
            <div className="p-5">
              <Skeleton className="h-56 w-full" />
            </div>
          ) : activityQuery.isError ? (
            <ConsoleEmpty>{resolveUserApiError(activityQuery.error)}</ConsoleEmpty>
          ) : hasActivity ? (
            <ActivityBarChart points={activityPoints} height={280} />
          ) : (
            <ConsoleEmpty>最近 14 天没有活动记录。</ConsoleEmpty>
          )}
        </ConsoleSection>
      </div>

      {editOpen ? (
        <EditUserDialog name={username} bio={spec?.bio} onClose={() => setEditOpen(false)} />
      ) : null}
      {passwordOpen ? (
        <ResetPasswordDialog name={username} onClose={() => setPasswordOpen(false)} />
      ) : null}
      {groupOpen ? (
        <ChangeGroupDialog
          name={username}
          currentGroup={group?.metadata?.name}
          onClose={() => setGroupOpen(false)}
        />
      ) : null}

      <ConfirmDialog
        open={statusConfirmOpen}
        onOpenChange={setStatusConfirmOpen}
        title={disabled ? "启用用户" : "禁用用户"}
        desc={
          disabled
            ? `确定启用「${displayName}」？`
            : `确定禁用「${displayName}」？禁用后该用户的会话和设备会被注销。`
        }
        confirmText={disabled ? "启用" : "禁用"}
        destructive={!disabled}
        isLoading={statusMutation.isPending}
        handleConfirm={() => statusMutation.mutate()}
      />
      <ConfirmDialog
        open={deleteConfirmOpen}
        onOpenChange={setDeleteConfirmOpen}
        title="删除用户"
        desc={`确定删除「${displayName}」？该用户的身份绑定也会被移除，此操作无法恢复。`}
        confirmText="删除"
        destructive
        isLoading={deleteMutation.isPending}
        handleConfirm={() => deleteMutation.mutate()}
      />
      <ConfirmDialog
        open={groupRemoveConfirmOpen}
        onOpenChange={setGroupRemoveConfirmOpen}
        title="移除用户组"
        desc={`确定将「${displayName}」移出当前用户组？用户将失去该用户组继承的角色。`}
        confirmText="移除"
        destructive
        isLoading={removeGroupMutation.isPending}
        handleConfirm={() => removeGroupMutation.mutate()}
      />
    </ConsolePage>
  );
}

function providerLabel(registrationId: string | undefined, configuredName?: string): string {
  if (configuredName) {
    return configuredName;
  }
  if (!registrationId) {
    return "未知身份";
  }
  return PROVIDER_LABELS[registrationId] ?? registrationId;
}

function ConnectionIcon({ registrationId, logo }: { registrationId?: string; logo?: string }) {
  if (registrationId === "feishu") {
    return <IconFeishu size={20} className="size-5" />;
  }
  if (logo) {
    return <img src={logo} alt="" className="size-5 rounded" />;
  }
  if (registrationId === "passkey") {
    return <Fingerprint className="size-5" />;
  }
  return <Link2 className="size-5" />;
}
