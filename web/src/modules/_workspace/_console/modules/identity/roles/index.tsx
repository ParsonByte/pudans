"use client";

import { useMemo, useState } from "react";

import type { Role } from "@pudans-dev/api-client";
import { useQueryClient } from "@tanstack/react-query";
import { AddIcon } from "tdesign-icons-react";
import { Breadcrumb, Button, Card, Empty, Skeleton } from "tdesign-react";

import { confirmGlobalDialog } from "@/components/ui";
import { usePermission } from "@/hooks/utils/use-permission";
import { useQueryParams } from "@/hooks/utils/use-query-params";

import { consoleErrorMessage } from "../../../composables/_errors";
import { useConsoleSession } from "../../../composables/_session";
import { IdentityLoadState } from "../components/identity-load-state";
import { ROLE_PERMISSIONS, ROLE_TEMPLATE_LABEL, isBuiltinRole, roleDisplayName } from "../composables/_role";
import { RoleDetailPanel } from "./components/role-detail-panel";
import { RoleEditorModal } from "./components/role-editor-modal";
import { RoleListPanel } from "./components/role-list-panel";
import { useDeleteRole } from "./composables/_role-actions";
import { useRolesQuery } from "./composables/_role-queries";

const ROLE_QUERY_KEY = ["console", "roles"] as const;
const { BreadcrumbItem } = Breadcrumb;

export function RolesView() {
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

        confirmGlobalDialog({
            cancelText: "继续编辑",
            confirmText: "放弃并切换",
            content: "切换角色会丢失当前未保存的菜单和操作权限变更。",
            danger: true,
            onConfirm: applySelection,
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

        confirmGlobalDialog({
            cancelText: "继续编辑",
            confirmText: "放弃并新建",
            content: "新建角色会关闭当前权限草稿，未保存的变更将不会保留。",
            danger: true,
            onConfirm: open,
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
        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: "删除",
            content: `确定删除角色 ${roleDisplayName(role)} 吗？删除后无法恢复。${
                dirty && deletingSelectedRole ? "当前未保存的权限变更也会一并丢弃。" : ""
            }`,
            danger: true,
            onConfirm: () => {
                void deleteRole.mutateAsync(role.metadata.name).then(() => {
                    if (deletingSelectedRole) {
                        setDirty(false);
                        setParam("role", null);
                    }
                });
            },
            title: "删除角色",
        });
    };

    if (sessionQuery.isPending) {
        return (
            <div className="flex w-full min-w-0 flex-col gap-4">
                <RolesBreadcrumb />
                <Card bordered={false} shadow={false}>
                    <Skeleton animation="gradient" theme="paragraph" />
                </Card>
            </div>
        );
    }

    if (sessionQuery.error) {
        return (
            <div className="flex w-full min-w-0 flex-col gap-4">
                <RolesBreadcrumb />
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

    if (!can(ROLE_PERMISSIONS.view)) {
        return (
            <div className="flex w-full min-w-0 flex-col gap-4">
                <RolesBreadcrumb />
                <IdentityLoadState description="当前账号没有查看角色的权限。" title="无权查看" />
            </div>
        );
    }

    return (
        <>
            <div className="flex w-full min-w-0 flex-col gap-4">
                <RolesBreadcrumb />
                <Card bordered={false} bodyClassName="!p-0" className="min-w-0 overflow-hidden" shadow={false}>
                    <div className="flex min-w-0 flex-col lg:flex-row">
                        <div className="w-full shrink-0 border-b border-[var(--td-component-border)] p-4 lg:w-[232px] lg:border-r lg:border-b-0 lg:p-6">
                            <RoleListPanel
                                error={rolesQuery.error}
                                loading={rolesQuery.isPending}
                                onCreate={canCreate ? openCreate : undefined}
                                onRetry={rolesQuery.error ? () => void rolesQuery.refetch() : undefined}
                                onSelect={selectRole}
                                roles={roles}
                                selectedName={selectedRole?.metadata.name}
                            />
                        </div>
                        <div className="min-w-0 flex-1">
                            {rolesQuery.isPending ? (
                                <RoleDetailSkeleton />
                            ) : rolesQuery.error ? (
                                <RoleDetailError error={rolesQuery.error} onRetry={() => void rolesQuery.refetch()} />
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
                                <RoleDetailEmpty canCreate={canCreate} onCreate={openCreate} />
                            )}
                        </div>
                    </div>
                </Card>
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

function RolesBreadcrumb() {
    return (
        <Breadcrumb>
            <BreadcrumbItem>身份与权限</BreadcrumbItem>
            <BreadcrumbItem>角色</BreadcrumbItem>
        </Breadcrumb>
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
        <div className="min-h-[480px] p-4 lg:p-6">
            <Skeleton animation="gradient" theme="paragraph" />
        </div>
    );
}

function RoleDetailError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
    return (
        <div className="flex min-h-[420px] items-center justify-center p-6">
            <Empty
                action={
                    <Button onClick={onRetry} theme="primary">
                        重试
                    </Button>
                }
                description={consoleErrorMessage(error)}
                title="角色列表加载失败"
                type="fail"
            />
        </div>
    );
}

function RoleDetailEmpty({ canCreate, onCreate }: { canCreate: boolean; onCreate: () => void }) {
    return (
        <div className="flex min-h-[420px] items-center justify-center p-6">
            <Empty
                action={
                    canCreate ? (
                        <Button icon={<AddIcon />} onClick={onCreate} theme="primary">
                            新建角色
                        </Button>
                    ) : undefined
                }
                title="暂无角色"
                type="empty"
            />
        </div>
    );
}
