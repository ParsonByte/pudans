"use client";

import { useMemo, useState } from "react";

import { ProCard } from "@ant-design/pro-components";
import type { Role } from "@pudans-dev/api-client";
import { useQueryClient } from "@tanstack/react-query";
import { App, Button, Empty, Result, Skeleton } from "antd";
import { Plus, RefreshCw } from "lucide-react";

import { usePermission } from "@/hooks/utils/use-permission";
import { useQueryParams } from "@/hooks/utils/use-query-params";

import { consoleErrorMessage } from "../../../composables/_errors";
import { useConsoleSession } from "../../../composables/_session";
import { ROLE_PERMISSIONS, ROLE_TEMPLATE_LABEL, isBuiltinRole, roleDisplayName } from "../composables/_role";
import { RoleDetailPanel } from "./components/role-detail-panel";
import { RoleEditorModal } from "./components/role-editor-modal";
import { RoleListPanel } from "./components/role-list-panel";
import { useDeleteRole } from "./composables/_role-actions";
import { useRolesQuery } from "./composables/_role-queries";

const ROLE_QUERY_KEY = ["console", "roles"] as const;

export function RolesView() {
    const { modal } = App.useApp();
    const queryClient = useQueryClient();
    const { searchParams, setParam } = useQueryParams();
    const sessionQuery = useConsoleSession();
    const rolesQuery = useRolesQuery();
    const deleteRole = useDeleteRole();
    const { can } = usePermission({ permissions: sessionQuery.data?.uiPermissions }, ROLE_PERMISSIONS.view);
    const canCreate = can(ROLE_PERMISSIONS.create);
    const canDelete = can(ROLE_PERMISSIONS.delete);
    const canUpdate = can(ROLE_PERMISSIONS.update);
    const [dirty, setDirty] = useState(false);
    const [panelRevision, setPanelRevision] = useState(0);
    const [editorRole, setEditorRole] = useState<Role | "create" | null>(null);

    const activeRoles = useMemo(() => rolesQuery.data ?? [], [rolesQuery.data]);
    const roles = useMemo(() => visibleRoles(activeRoles), [activeRoles]);
    const requestedRole = searchParams.get("role") ?? undefined;
    const selectedRole = useMemo(
        () => roles.find((role) => role.metadata.name === requestedRole) ?? roles[0],
        [requestedRole, roles],
    );

    const refreshRole = () => {
        void queryClient.invalidateQueries({ queryKey: ROLE_QUERY_KEY });
    };
    const discardDraft = () => {
        setDirty(false);
        setPanelRevision((revision) => revision + 1);
    };
    const selectRole = (roleName: string) => {
        if (roleName === selectedRole?.metadata.name) {
            if (requestedRole !== roleName) {
                setParam("role", roleName);
            }
            return;
        }

        const applySelection = () => {
            setDirty(false);
            setParam("role", roleName);
        };

        if (!dirty) {
            applySelection();
            return;
        }

        modal.confirm({
            cancelText: "继续编辑",
            content: "切换角色会丢失当前未保存的菜单和操作权限变更。",
            okButtonProps: { danger: true },
            okText: "放弃并切换",
            onOk: applySelection,
            title: "放弃未保存的变更？",
        });
    };
    const openCreate = () => {
        const open = () => {
            discardDraft();
            setEditorRole("create");
        };

        if (!dirty) {
            open();
            return;
        }

        modal.confirm({
            cancelText: "继续编辑",
            content: "新建角色会关闭当前权限草稿，未保存的变更将不会保留。",
            okButtonProps: { danger: true },
            okText: "放弃并新建",
            onOk: open,
            title: "放弃未保存的变更？",
        });
    };
    const openEdit = (role: Role) => {
        setEditorRole(role);
    };
    const handleEditorSaved = (roleName: string) => {
        setEditorRole(null);
        setParam("role", roleName);
    };
    const confirmDelete = (role: Role) => {
        if (!canDelete || isBuiltinRole(role)) {
            return;
        }

        const deletingSelectedRole = selectedRole?.metadata.name === role.metadata.name;
        modal.confirm({
            cancelText: "取消",
            content: `确定删除角色 ${roleDisplayName(role)} 吗？删除后无法恢复。${
                dirty && deletingSelectedRole ? "当前未保存的权限变更也会一并丢弃。" : ""
            }`,
            okButtonProps: { danger: true },
            okText: "删除",
            onOk: async () => {
                await deleteRole.mutateAsync(role.metadata.name);
                if (deletingSelectedRole) {
                    setDirty(false);
                    setParam("role", null);
                }
            },
            title: "删除角色",
        });
    };

    if (sessionQuery.isPending) {
        return <Skeleton active paragraph={{ rows: 10 }} />;
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

    if (!can(ROLE_PERMISSIONS.view)) {
        return <Result status="403" subTitle="当前账号没有查看角色的权限。" title="无权查看" />;
    }

    return (
        <>
            <div className="grid min-w-0 gap-4 lg:grid-cols-[300px_minmax(0,1fr)] xl:grid-cols-[340px_minmax(0,1fr)]">
                <RoleListPanel
                    canCreate={canCreate}
                    loading={rolesQuery.isPending}
                    onCreate={openCreate}
                    onSelect={selectRole}
                    roles={roles}
                    selectedName={selectedRole?.metadata.name}
                />
                {rolesQuery.isPending ? (
                    <RoleDetailSkeleton />
                ) : rolesQuery.error ? (
                    <RoleLoadError error={rolesQuery.error} onRetry={() => void rolesQuery.refetch()} />
                ) : selectedRole ? (
                    <RoleDetailPanel
                        allowedPermissions={sessionQuery.data?.uiPermissions}
                        canDelete={canDelete}
                        canUpdate={canUpdate}
                        key={`${selectedRole.metadata.name}:${panelRevision}`}
                        onDelete={confirmDelete}
                        onDirtyChange={setDirty}
                        onEditRole={openEdit}
                        onRefresh={refreshRole}
                        role={selectedRole}
                        roles={activeRoles}
                    />
                ) : (
                    <EmptyRoleState canCreate={canCreate} onCreate={openCreate} />
                )}
            </div>
            {editorRole ? (
                <RoleEditorModal
                    onClose={() => setEditorRole(null)}
                    onSaved={handleEditorSaved}
                    role={editorRole === "create" ? undefined : editorRole}
                />
            ) : null}
        </>
    );
}

function visibleRoles(items?: Role[]): Role[] {
    return (items ?? [])
        .filter((role) => role.metadata.labels?.[ROLE_TEMPLATE_LABEL] !== "true" && !role.metadata.deletionTimestamp)
        .sort((left, right) => {
            const builtinOrder = Number(isBuiltinRole(right)) - Number(isBuiltinRole(left));
            if (builtinOrder !== 0) {
                return builtinOrder;
            }

            return (
                roleDisplayName(left).localeCompare(roleDisplayName(right), "zh-Hans-CN", {
                    sensitivity: "base",
                }) || left.metadata.name.localeCompare(right.metadata.name)
            );
        });
}

function RoleDetailSkeleton() {
    return (
        <ProCard className="min-h-[480px] overflow-hidden !rounded-[24px]" styles={{ body: { padding: 20 } }}>
            <Skeleton active paragraph={{ rows: 12 }} />
        </ProCard>
    );
}

function RoleLoadError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
    return (
        <ProCard className="min-h-[480px] overflow-hidden !rounded-[24px]">
            <Result
                extra={
                    <Button icon={<RefreshCw size={14} />} onClick={onRetry} type="primary">
                        重试
                    </Button>
                }
                status="error"
                subTitle={consoleErrorMessage(error)}
                title="角色列表加载失败"
            />
        </ProCard>
    );
}

function EmptyRoleState({ canCreate, onCreate }: { canCreate: boolean; onCreate: () => void }) {
    return (
        <ProCard
            className="min-h-[480px] overflow-hidden !rounded-[24px]"
            styles={{ body: { height: "100%", padding: 0 } }}
        >
            <div className="flex min-h-[480px] flex-col items-center justify-center gap-3">
                <Empty description="暂无角色" image={Empty.PRESENTED_IMAGE_SIMPLE}>
                    {canCreate ? (
                        <Button icon={<Plus size={14} />} onClick={onCreate} type="primary">
                            新建角色
                        </Button>
                    ) : null}
                </Empty>
            </div>
        </ProCard>
    );
}
