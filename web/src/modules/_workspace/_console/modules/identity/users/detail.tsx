"use client";

import { useEffect, useState } from "react";

import Link from "next/link";
import { useRouter } from "next/navigation";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import { ArrowLeftIcon } from "tdesign-icons-react";
import { Breadcrumb, Button, Skeleton, Typography } from "tdesign-react";

import { confirmGlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { usePermission } from "@/hooks/utils/use-permission";

import { consoleErrorMessage } from "../../../composables/_errors";
import { useConsoleSession } from "../../../composables/_session";
import { IdentityLoadState } from "../components/identity-load-state";
import { ResetUserPasswordModal } from "./components/reset-user-password-modal";
import { UserDetailAccess } from "./components/user-detail-access";
import { UserDetailActivity } from "./components/user-detail-activity";
import { UserDetailConnections } from "./components/user-detail-connections";
import { UserDetailInformation } from "./components/user-detail-information";
import { UserDetailProfile } from "./components/user-detail-profile";
import { UserEditorModal } from "./components/user-editor-modal";
import { useDeleteUser, useSetUserStatus } from "./composables/_user-actions";
import { useUserGroupOptions } from "./composables/_user-groups";
import { USER_PERMISSIONS } from "./composables/_user-permissions";
import { userListPath } from "./composables/_user-route";

type UserDetailViewProps = {
    name: string;
    workspace: string;
};

export function UserDetailView({ name, workspace }: UserDetailViewProps) {
    const router = useRouter();
    const prompt = useMessagePrompt();
    const sessionQuery = useConsoleSession();
    const { options: groupOptions } = useUserGroupOptions();
    const { can } = usePermission({ permissions: sessionQuery.data?.uiPermissions }, USER_PERMISSIONS.view);
    const canDelete = can(USER_PERMISSIONS.delete);
    const canGroup = can(USER_PERMISSIONS.group);
    const canPassword = can(USER_PERMISSIONS.password);
    const canStatus = can(USER_PERMISSIONS.status);
    const canUpdate = can(USER_PERMISSIONS.update);
    const [editingOpen, setEditingOpen] = useState(false);
    const [resetOpen, setResetOpen] = useState(false);
    const deleteUser = useDeleteUser();
    const setStatus = useSetUserStatus();
    const userQuery = useQuery({
        queryKey: ["console", "users", "detail", name],
        queryFn: async () => (await apiClient.user.getUser({ name })).data,
    });
    const groupQuery = useQuery({
        queryKey: ["console", "users", "group", name],
        queryFn: async () => {
            try {
                return (await apiClient.user.getUserGroup({ name })).data;
            } catch (error) {
                if (isAxiosError(error) && error.response?.status === 400) {
                    return null;
                }

                throw error;
            }
        },
        retry: false,
    });
    const groupLabel = groupQuery.isPending
        ? "加载中"
        : groupQuery.isError
          ? "无法读取"
          : (groupQuery.data?.displayName ?? "未分组");

    useEffect(() => {
        if (groupQuery.error) {
            prompt.error({ content: consoleErrorMessage(groupQuery.error) });
        }
    }, [groupQuery.error, prompt]);

    if (sessionQuery.isPending) {
        return <Skeleton theme="paragraph" />;
    }

    if (sessionQuery.error) {
        return (
            <IdentityLoadState
                action={
                    <Button onClick={() => void sessionQuery.refetch()} theme="primary">
                        重试
                    </Button>
                }
                description={consoleErrorMessage(sessionQuery.error)}
                title="无法读取当前会话权限"
            />
        );
    }

    if (!can(USER_PERMISSIONS.view)) {
        return <IdentityLoadState description="当前账号没有查看用户详情的权限。" title="无权查看" />;
    }

    if (userQuery.isPending) {
        return (
            <div className="flex flex-col gap-4">
                <Skeleton theme="paragraph" />
                <Skeleton theme="avatar-text" />
            </div>
        );
    }

    if (userQuery.error || !userQuery.data) {
        return (
            <IdentityLoadState
                action={
                    <div className="flex flex-wrap justify-center gap-2">
                        <Button onClick={() => void userQuery.refetch()}>重试</Button>
                        <Button onClick={() => router.push(userListPath(workspace))} theme="primary">
                            返回用户列表
                        </Button>
                    </div>
                }
                description={userQuery.error ? consoleErrorMessage(userQuery.error) : "用户不存在或已被删除。"}
                title="用户资料加载失败"
            />
        );
    }

    const user = userQuery.data;
    const displayName = user.spec.displayName || user.metadata.name;
    const canEdit = canUpdate || (canGroup && groupQuery.isSuccess);

    const confirmToggleStatus = () => {
        confirmGlobalDialog({
            confirmText: user.spec.disabled ? "启用账号" : "禁用账号",
            content: user.spec.disabled
                ? "启用后该账号可以重新登录。"
                : "禁用后该账号将无法登录，已有会话会按后端策略处理。",
            danger: !user.spec.disabled,
            onConfirm: () => {
                void setStatus.mutateAsync({
                    disabled: !user.spec.disabled,
                    name: user.metadata.name,
                });
            },
            title: user.spec.disabled ? "启用用户" : "禁用用户",
        });
    };
    const confirmDelete = () => {
        confirmGlobalDialog({
            confirmText: "删除",
            content: `确定删除用户 ${displayName} 吗？删除后无法恢复。`,
            danger: true,
            onConfirm: () => {
                void deleteUser.mutateAsync(user.metadata.name, {
                    onSuccess: () => router.push(userListPath(workspace)),
                });
            },
            title: "删除用户",
        });
    };

    return (
        <>
            <div className="flex w-full min-w-0 flex-col gap-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0">
                        <Breadcrumb
                            options={[
                                {
                                    content: <Link href={userListPath(workspace)}>用户</Link>,
                                },
                                { content: "详情" },
                            ]}
                        />
                        <Typography.Title className="mt-2" level="h3">
                            {displayName}
                        </Typography.Title>
                    </div>
                    <Button icon={<ArrowLeftIcon />} onClick={() => router.push(userListPath(workspace))}>
                        返回用户列表
                    </Button>
                </div>
                <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-12">
                    <div className="flex min-w-0 flex-col gap-4 lg:col-span-5 xl:col-span-4">
                        <UserDetailProfile
                            canDelete={canDelete}
                            canEdit={canEdit}
                            canPassword={canPassword}
                            canStatus={canStatus}
                            groupLabel={groupLabel}
                            onDelete={confirmDelete}
                            onEdit={() => setEditingOpen(true)}
                            onResetPassword={() => setResetOpen(true)}
                            onToggleStatus={confirmToggleStatus}
                            statusPending={setStatus.isPending}
                            user={user}
                        />
                        <UserDetailAccess name={name} />
                        <UserDetailConnections name={name} />
                    </div>
                    <div className="flex min-w-0 flex-col gap-4 lg:col-span-7 xl:col-span-8">
                        <UserDetailInformation groupLabel={groupLabel} user={user} />
                        <UserDetailActivity name={name} />
                    </div>
                </div>
            </div>
            {editingOpen ? (
                <UserEditorModal
                    canGroup={canGroup && groupQuery.isSuccess}
                    canUpdate={canUpdate}
                    currentGroup={groupQuery.data?.metadata.name}
                    detail={user}
                    groupOptions={groupOptions}
                    onClose={() => setEditingOpen(false)}
                    onSaved={() => setEditingOpen(false)}
                />
            ) : null}
            {resetOpen ? (
                <ResetUserPasswordModal
                    displayName={displayName}
                    name={user.metadata.name}
                    onClose={() => setResetOpen(false)}
                    onSaved={() => setResetOpen(false)}
                />
            ) : null}
        </>
    );
}
