"use client";

import { useState } from "react";

import Link from "next/link";
import { useRouter } from "next/navigation";

import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Alert, App, Breadcrumb, Button, Flex, Result, Skeleton, Typography } from "antd";
import { isAxiosError } from "axios";
import { ArrowLeft } from "lucide-react";

import { BentoGrid } from "@/components/layouts/bento-grid";
import { usePermission } from "@/hooks/utils/use-permission";

import { consoleErrorMessage } from "../../../composables/_errors";
import { useConsoleSession } from "../../../composables/_session";
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
    const { modal } = App.useApp();
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

    if (sessionQuery.isPending) {
        return <Skeleton active paragraph={{ rows: 8 }} />;
    }

    if (sessionQuery.error) {
        return (
            <Result
                extra={
                    <Button onClick={() => void sessionQuery.refetch()} type="primary">
                        重试
                    </Button>
                }
                status="error"
                subTitle={consoleErrorMessage(sessionQuery.error)}
                title="无法读取当前会话权限"
            />
        );
    }

    if (!can(USER_PERMISSIONS.view)) {
        return <Result status="403" subTitle="当前账号没有查看用户详情的权限。" title="无权查看" />;
    }

    if (userQuery.isPending) {
        return <Skeleton active paragraph={{ rows: 10 }} />;
    }

    if (userQuery.error || !userQuery.data) {
        return (
            <Result
                extra={
                    <Flex gap={8} wrap>
                        <Button onClick={() => void userQuery.refetch()}>重试</Button>
                        <Link href={userListPath(workspace)}>
                            <Button type="primary">返回用户列表</Button>
                        </Link>
                    </Flex>
                }
                status="error"
                subTitle={userQuery.error ? consoleErrorMessage(userQuery.error) : "用户不存在或已被删除。"}
                title="用户资料加载失败"
            />
        );
    }

    const user = userQuery.data;
    const displayName = user.spec.displayName || user.metadata.name;
    const canEdit = canUpdate || (canGroup && groupQuery.isSuccess);

    const confirmToggleStatus = () => {
        modal.confirm({
            cancelText: "取消",
            content: user.spec.disabled
                ? "启用后该账号可以重新登录。"
                : "禁用后该账号将无法登录，已有会话会按后端策略处理。",
            okButtonProps: user.spec.disabled ? undefined : { danger: true },
            okText: user.spec.disabled ? "启用账号" : "禁用账号",
            onOk: () =>
                setStatus.mutateAsync({
                    disabled: !user.spec.disabled,
                    name: user.metadata.name,
                }),
            title: user.spec.disabled ? "启用用户" : "禁用用户",
        });
    };
    const confirmDelete = () => {
        modal.confirm({
            cancelText: "取消",
            content: `确定删除用户 ${displayName} 吗？删除后无法恢复。`,
            okButtonProps: { danger: true },
            okText: "删除",
            onOk: () =>
                deleteUser.mutateAsync(user.metadata.name, {
                    onSuccess: () => router.push(userListPath(workspace)),
                }),
            title: "删除用户",
        });
    };

    return (
        <>
            <Flex className="w-full" gap={16} vertical>
                <Flex align="flex-start" gap={16} justify="space-between" wrap>
                    <div className="min-w-0">
                        <Breadcrumb
                            items={[
                                { title: <Link href={userListPath(workspace)}>用户</Link> },
                                { title: displayName },
                            ]}
                        />
                        <Typography.Title className="!mt-2 !mb-0" level={3}>
                            用户详情
                        </Typography.Title>
                    </div>
                    <Link href={userListPath(workspace)}>
                        <Button icon={<ArrowLeft size={15} />}>返回用户列表</Button>
                    </Link>
                </Flex>
                <BentoGrid className="bento-grid--dashboard md:!grid-cols-1 lg:!grid-cols-12">
                    <Flex className="min-w-0 lg:col-span-5 xl:col-span-4" gap={16} vertical>
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
                    </Flex>
                    <Flex className="min-w-0 lg:col-span-7 xl:col-span-8" gap={16} vertical>
                        <UserDetailInformation groupLabel={groupLabel} user={user} />
                        <UserDetailActivity name={name} />
                    </Flex>
                </BentoGrid>
                {groupQuery.isError ? (
                    <Alert closable message="用户组信息加载失败，编辑用户组功能已暂时禁用。" showIcon type="warning" />
                ) : null}
            </Flex>
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
