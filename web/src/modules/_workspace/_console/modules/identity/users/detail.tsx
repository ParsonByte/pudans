"use client";

import { useEffect, useState } from "react";

import Link from "next/link";
import { useRouter } from "next/navigation";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import { differenceInCalendarDays } from "date-fns";
import { Breadcrumb, Button, Card, Col, Row, Skeleton } from "tdesign-react";

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
import { UserDetailTeammates } from "./components/user-detail-teammates";
import { UserEditorModal } from "./components/user-editor-modal";
import { UserGroupTag, UserStatusTag } from "./components/user-tags";
import { useDeleteUser, useSetUserStatus } from "./composables/_user-actions";
import { useUserGroupOptions } from "./composables/_user-groups";
import { USER_PERMISSIONS } from "./composables/_user-permissions";
import { userListPath } from "./composables/_user-route";

type UserDetailViewProps = {
    name: string;
    workspace: string;
};

function joinedDayCount(value?: string): number | undefined {
    if (!value) {
        return undefined;
    }

    const joinedAt = new Date(value);

    return Number.isNaN(joinedAt.getTime())
        ? undefined
        : Math.max(1, differenceInCalendarDays(new Date(), joinedAt) + 1);
}

function greeting(): string {
    const hour = new Date().getHours();

    if (hour < 12) {
        return "上午好";
    }

    return hour < 18 ? "下午好" : "晚上好";
}

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
    const viewerName = sessionQuery.data?.displayName || sessionQuery.data?.username || "当前账号";
    const canEdit = canUpdate || (canGroup && groupQuery.isSuccess);
    const days = joinedDayCount(user.spec.registeredAt);
    const welcomeText = days
        ? `${greeting()}，今天是${displayName}加入的第 ${days} 天～`
        : `${greeting()}，欢迎查看${displayName}的资料～`;

    const confirmToggleStatus = () => {
        confirmGlobalDialog({
            confirmText: user.spec.disabled ? "启用用户" : "禁用用户",
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
            <div className="flex min-w-0 flex-col gap-4">
                <Breadcrumb
                    options={[
                        {
                            content: <Link href={userListPath(workspace)}>用户</Link>,
                        },
                        { content: "个人中心" },
                    ]}
                />
                <Row gutter={[16, 16]}>
                    <Col lg={12} xl={9} xs={12}>
                        <div className="flex min-w-0 flex-col gap-4">
                            <Card bordered={false} shadow={false}>
                                <div className="flex flex-wrap items-center justify-between gap-4">
                                    <div className="min-w-0">
                                        <div className="text-xl font-medium break-words">
                                            Hi，{viewerName}
                                            {"，"}
                                            <span className="text-sm font-normal text-[var(--td-text-color-secondary)]">
                                                {welcomeText}
                                            </span>
                                        </div>
                                    </div>
                                    <div className="flex flex-wrap items-center gap-2">
                                        <UserStatusTag disabled={user.spec.disabled} />
                                        <UserGroupTag displayName={groupLabel === "未分组" ? undefined : groupLabel} />
                                    </div>
                                </div>
                            </Card>
                            <UserDetailInformation
                                canEdit={canEdit}
                                groupLabel={groupLabel}
                                onEdit={() => setEditingOpen(true)}
                                user={user}
                            />
                            <UserDetailActivity name={name} />
                        </div>
                    </Col>
                    <Col lg={12} xl={3} xs={12}>
                        <div className="flex min-w-0 flex-col gap-4">
                            <UserDetailProfile
                                canDelete={canDelete}
                                canPassword={canPassword}
                                canStatus={canStatus}
                                groupLabel={groupLabel}
                                onDelete={confirmDelete}
                                onResetPassword={() => setResetOpen(true)}
                                onToggleStatus={confirmToggleStatus}
                                statusPending={setStatus.isPending}
                                user={user}
                            />
                            <UserDetailTeammates
                                groupLabel={groupLabel}
                                groupName={groupQuery.data?.metadata.name}
                                name={name}
                                workspace={workspace}
                            />
                            <UserDetailAccess name={name} />
                            <UserDetailConnections name={name} />
                        </div>
                    </Col>
                </Row>
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
