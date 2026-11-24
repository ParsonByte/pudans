"use client";

import { useMemo, useState } from "react";

import { useParams } from "next/navigation";

import type { UserGroup } from "@pudans-dev/api-client";
import { useQueryClient } from "@tanstack/react-query";
import { AddIcon } from "tdesign-icons-react";
import { Breadcrumb, Button, Card, Empty, Skeleton } from "tdesign-react";

import { confirmGlobalDialog } from "@/components/ui";
import { usePermission } from "@/hooks/utils/use-permission";
import { useQueryParams } from "@/hooks/utils/use-query-params";

import { consoleErrorMessage } from "../../../composables/_errors";
import { useConsoleSession } from "../../../composables/_session";
import { IdentityLoadState } from "../components/identity-load-state";
import { USER_PERMISSIONS } from "../users/composables/_user-permissions";
import { UserGroupDetailPanel } from "./components/user-group-detail-panel";
import { UserGroupEditorModal } from "./components/user-group-editor-modal";
import { UserGroupListPanel } from "./components/user-group-list-panel";
import {
    USER_GROUP_PERMISSIONS,
    isBuiltinUserGroup,
    sortUserGroups,
    userGroupDisplayName,
} from "./composables/_user-group";
import { useDeleteUserGroup } from "./composables/_user-group-actions";
import { useUserGroupsQuery } from "./composables/_user-group-queries";

const USER_GROUP_QUERY_KEY = ["console", "user-groups"] as const;
const { BreadcrumbItem } = Breadcrumb;

export function UserGroupsView() {
    const { workspace } = useParams<{ workspace: string }>();
    const queryClient = useQueryClient();
    const { searchParams, setParam } = useQueryParams();
    const sessionQuery = useConsoleSession();
    const groupsQuery = useUserGroupsQuery();
    const deleteGroup = useDeleteUserGroup();
    const { can } = usePermission({ permissions: sessionQuery.data?.uiPermissions }, USER_GROUP_PERMISSIONS.view);
    const canCreate = can(USER_GROUP_PERMISSIONS.create);
    const canDelete = can(USER_GROUP_PERMISSIONS.delete);
    const canUpdate = can(USER_GROUP_PERMISSIONS.update);
    const canGroupMembers = can(USER_PERMISSIONS.group);
    const canViewUsers = can(USER_PERMISSIONS.view);
    const [editorGroup, setEditorGroup] = useState<UserGroup | "create" | null>(null);

    const groups = useMemo(() => sortUserGroups(groupsQuery.data ?? []), [groupsQuery.data]);
    const requestedGroup = searchParams.get("group") ?? undefined;
    const selectedGroup = useMemo(
        () => groups.find((group) => group.metadata.name === requestedGroup) ?? groups[0],
        [groups, requestedGroup],
    );

    const refreshGroups = () => {
        void queryClient.invalidateQueries({ queryKey: USER_GROUP_QUERY_KEY });
    };
    const selectGroup = (groupName: string) => {
        if (groupName !== requestedGroup) {
            setParam("group", groupName);
        }
    };
    const handleEditorSaved = (groupName: string) => {
        setEditorGroup(null);
        setParam("group", groupName);
    };
    const confirmDelete = (group: UserGroup) => {
        if (!canDelete || isBuiltinUserGroup(group)) {
            return;
        }

        const deletingSelected = selectedGroup?.metadata.name === group.metadata.name;
        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: "删除",
            content: `确定删除用户组 ${userGroupDisplayName(group)} 吗？组内成员会变为未分组，相关角色权限会被回收。删除后无法恢复。`,
            danger: true,
            onConfirm: () => {
                void deleteGroup.mutateAsync(group.metadata.name, {
                    onSuccess: () => {
                        if (deletingSelected) {
                            setParam("group", null);
                        }
                    },
                });
            },
            title: "删除用户组",
        });
    };

    if (sessionQuery.isPending) {
        return (
            <div className="flex w-full min-w-0 flex-col gap-4">
                <UserGroupsBreadcrumb />
                <Card bordered={false} shadow={false}>
                    <Skeleton animation="gradient" theme="paragraph" />
                </Card>
            </div>
        );
    }

    if (sessionQuery.error) {
        return (
            <div className="flex w-full min-w-0 flex-col gap-4">
                <UserGroupsBreadcrumb />
                <IdentityLoadState
                    action={
                        <Button onClick={() => void sessionQuery.refetch()} theme="primary">
                            重试
                        </Button>
                    }
                    description={consoleErrorMessage(sessionQuery.error)}
                    title="无法读取当前会话权限"
                />
            </div>
        );
    }

    if (!can(USER_GROUP_PERMISSIONS.view)) {
        return (
            <div className="flex w-full min-w-0 flex-col gap-4">
                <UserGroupsBreadcrumb />
                <IdentityLoadState description="当前账号没有查看用户组的权限。" title="无权查看" />
            </div>
        );
    }

    return (
        <>
            <div className="flex w-full min-w-0 flex-col gap-4">
                <UserGroupsBreadcrumb />
                <Card bordered={false} bodyClassName="!p-0" className="min-w-0 overflow-hidden" shadow={false}>
                    <div className="flex min-w-0 flex-col lg:flex-row">
                        <div className="w-full shrink-0 border-b border-[var(--td-component-border)] p-4 lg:w-[232px] lg:border-r lg:border-b-0 lg:p-6">
                            <UserGroupListPanel
                                error={groupsQuery.error}
                                groups={groups}
                                loading={groupsQuery.isPending}
                                onCreate={canCreate ? () => setEditorGroup("create") : undefined}
                                onRetry={groupsQuery.error ? () => void groupsQuery.refetch() : undefined}
                                onSelect={selectGroup}
                                selectedName={selectedGroup?.metadata.name}
                            />
                        </div>
                        <div className="min-w-0 flex-1">
                            {groupsQuery.isPending ? (
                                <UserGroupDetailSkeleton />
                            ) : groupsQuery.error ? (
                                <UserGroupDetailError
                                    error={groupsQuery.error}
                                    onRetry={() => void groupsQuery.refetch()}
                                />
                            ) : selectedGroup ? (
                                <UserGroupDetailPanel
                                    canDelete={canDelete}
                                    canGroupMembers={canGroupMembers}
                                    canUpdate={canUpdate}
                                    canViewUsers={canViewUsers}
                                    group={selectedGroup}
                                    key={selectedGroup.metadata.name}
                                    onDelete={confirmDelete}
                                    onEdit={setEditorGroup}
                                    onRefresh={refreshGroups}
                                    workspace={workspace}
                                />
                            ) : (
                                <UserGroupDetailEmpty canCreate={canCreate} onCreate={() => setEditorGroup("create")} />
                            )}
                        </div>
                    </div>
                </Card>
            </div>
            {editorGroup ? (
                <UserGroupEditorModal
                    group={editorGroup === "create" ? undefined : editorGroup}
                    onClose={() => setEditorGroup(null)}
                    onSaved={handleEditorSaved}
                />
            ) : null}
        </>
    );
}

function UserGroupsBreadcrumb() {
    return (
        <Breadcrumb>
            <BreadcrumbItem>身份与权限</BreadcrumbItem>
            <BreadcrumbItem>用户组</BreadcrumbItem>
        </Breadcrumb>
    );
}

function UserGroupDetailSkeleton() {
    return (
        <div className="min-h-[480px] p-4 lg:p-6">
            <Skeleton animation="gradient" theme="paragraph" />
        </div>
    );
}

function UserGroupDetailError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
    return (
        <div className="flex min-h-[420px] items-center justify-center p-6">
            <Empty
                action={
                    <Button onClick={onRetry} theme="primary">
                        重试
                    </Button>
                }
                description={consoleErrorMessage(error)}
                title="用户组列表加载失败"
                type="fail"
            />
        </div>
    );
}

function UserGroupDetailEmpty({ canCreate, onCreate }: { canCreate: boolean; onCreate: () => void }) {
    return (
        <div className="flex min-h-[420px] items-center justify-center p-6">
            <Empty
                action={
                    canCreate ? (
                        <Button icon={<AddIcon />} onClick={onCreate} theme="primary">
                            新建用户组
                        </Button>
                    ) : undefined
                }
                title="暂无用户组"
                type="empty"
            />
        </div>
    );
}
