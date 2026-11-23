"use client";

import { useMemo, useState } from "react";

import { useParams } from "next/navigation";

import type { UserGroup } from "@pudans-dev/api-client";
import { useQueryClient } from "@tanstack/react-query";
import { AddIcon, RefreshIcon, UsergroupAddIcon } from "tdesign-icons-react";
import { Button, Card, Skeleton } from "tdesign-react";

import { confirmGlobalDialog } from "@/components/ui";
import { usePermission } from "@/hooks/utils/use-permission";
import { useQueryParams } from "@/hooks/utils/use-query-params";

import { consoleErrorMessage } from "../../../composables/_errors";
import { formatCountOrDash } from "../../../composables/_format";
import { useConsoleSession } from "../../../composables/_session";
import { IdentityLoadState } from "../components/identity-load-state";
import { IdentityPageOverview } from "../components/identity-page-overview";
import { USER_PERMISSIONS } from "../users/composables/_user-permissions";
import { UserGroupDetailPanel } from "./components/user-group-detail-panel";
import { UserGroupEditorModal } from "./components/user-group-editor-modal";
import { UserGroupListPanel } from "./components/user-group-list-panel";
import {
    USER_GROUP_PERMISSIONS,
    isBuiltinUserGroup,
    sortUserGroups,
    userGroupDisplayName,
    userGroupRoleNames,
} from "./composables/_user-group";
import { useDeleteUserGroup } from "./composables/_user-group-actions";
import { useUserGroupsQuery } from "./composables/_user-group-queries";

const USER_GROUP_QUERY_KEY = ["console", "user-groups"] as const;

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
    const groupCounts = useMemo(() => {
        const builtin = groups.filter(isBuiltinUserGroup).length;
        return {
            builtin,
            custom: groups.length - builtin,
            roles: groups.reduce((total, group) => total + userGroupRoleNames(group).length, 0),
        };
    }, [groups]);
    const overview = groupsQuery.isPending ? undefined : { total: groups.length, ...groupCounts };
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
            <Card>
                <Skeleton animation="gradient" theme="paragraph" />
            </Card>
        );
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

    if (!can(USER_GROUP_PERMISSIONS.view)) {
        return <IdentityLoadState description="当前账号没有查看用户组的权限。" title="无权查看" />;
    }

    return (
        <>
            <div className="flex min-w-0 flex-col gap-4">
                <IdentityPageOverview
                    actions={
                        <>
                            {canCreate ? (
                                <Button icon={<AddIcon />} onClick={() => setEditorGroup("create")} theme="primary">
                                    新建用户组
                                </Button>
                            ) : null}
                            <Button
                                icon={<RefreshIcon />}
                                loading={groupsQuery.isFetching}
                                onClick={refreshGroups}
                                variant="outline"
                            >
                                刷新
                            </Button>
                        </>
                    }
                    icon={<UsergroupAddIcon />}
                    metrics={[
                        { label: "用户组总数", value: formatCountOrDash(overview?.total) },
                        { label: "系统内置", value: formatCountOrDash(overview?.builtin) },
                        { label: "自定义用户组", value: formatCountOrDash(overview?.custom) },
                        { label: "角色绑定条目", value: formatCountOrDash(overview?.roles) },
                    ]}
                    title="用户组与成员"
                />
                <div className="grid min-w-0 gap-4 lg:grid-cols-[300px_minmax(0,1fr)] xl:grid-cols-[340px_minmax(0,1fr)]">
                    <UserGroupListPanel
                        groups={groups}
                        loading={groupsQuery.isPending}
                        onCreate={canCreate ? () => setEditorGroup("create") : undefined}
                        onSelect={selectGroup}
                        selectedName={selectedGroup?.metadata.name}
                    />
                    {groupsQuery.isPending ? (
                        <UserGroupDetailSkeleton />
                    ) : groupsQuery.error ? (
                        <IdentityLoadState
                            action={
                                <Button onClick={() => void groupsQuery.refetch()} theme="primary">
                                    重试
                                </Button>
                            }
                            description={consoleErrorMessage(groupsQuery.error)}
                            title="用户组列表加载失败"
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
                        <IdentityLoadState
                            action={
                                canCreate ? (
                                    <Button icon={<AddIcon />} onClick={() => setEditorGroup("create")} theme="primary">
                                        新建用户组
                                    </Button>
                                ) : undefined
                            }
                            title="暂无用户组"
                            type="empty"
                        />
                    )}
                </div>
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

function UserGroupDetailSkeleton() {
    return (
        <Card className="min-h-[480px] overflow-hidden">
            <Skeleton animation="gradient" theme="paragraph" />
        </Card>
    );
}
