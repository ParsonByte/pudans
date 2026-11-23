"use client";

import { useEffect, useMemo, useRef, useState } from "react";

import { ProCard } from "@ant-design/pro-components";
import type { Role } from "@pudans-dev/api-client";
import { Alert, Button, Skeleton, Tabs, type TabsProps, theme } from "antd";

import { useUnsavedChangesWarning } from "@/hooks/ui/use-unsaved-changes-warning";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { usePermissionCatalogQuery } from "../../composables/_permission-catalog";
import { isBuiltinRole } from "../../composables/_role";
import { useUpdateRole } from "../composables/_role-actions";
import {
    type RolePermissionDraft,
    buildRoleCatalogIndex,
    buildRolePermissionPayload,
    buildRolePermissionView,
    buildRoleWorkspaceAccess,
    cloneRolePermissionDraft,
    collectRoleRuleRows,
    countRolePermissionDraft,
    emptyRolePermissionDraft,
    permissionDraftSignature,
    sameRolePermissionDraft,
} from "../composables/_role-permissions";
import { RoleAccessPermissions } from "./role-access-permissions";
import { RoleDataPermissions } from "./role-data-permissions";
import { RoleMenuPermissions } from "./role-menu-permissions";
import { RoleOperationPermissions } from "./role-operation-permissions";
import { RoleSummary } from "./role-summary";

type RoleDetailPanelProps = {
    allowedPermissions?: string[];
    canDelete: boolean;
    canUpdate: boolean;
    onDelete: (role: Role) => void;
    onDirtyChange: (dirty: boolean) => void;
    onEditRole: (role: Role) => void;
    onRefresh: () => void;
    role: Role;
    roles: Role[];
};

export function RoleDetailPanel({
    allowedPermissions,
    canDelete,
    canUpdate,
    onDelete,
    onDirtyChange,
    onEditRole,
    onRefresh,
    role,
    roles,
}: RoleDetailPanelProps) {
    const { token } = theme.useToken();
    const catalogQuery = usePermissionCatalogQuery();
    const updateRole = useUpdateRole();
    const [activeTab, setActiveTab] = useState("menu");
    const [draft, setDraft] = useState<RolePermissionDraft>(emptyRolePermissionDraft);
    const [savedSignature, setSavedSignature] = useState<string>();
    const [conflict, setConflict] = useState(false);
    const appliedSignature = useRef<string | undefined>(undefined);
    const draftRef = useRef(draft);

    const rolesByName = useMemo(() => new Map(roles.map((candidate) => [candidate.metadata.name, candidate])), [roles]);
    const index = useMemo(() => buildRoleCatalogIndex(catalogQuery.data), [catalogQuery.data]);
    const permissionView = useMemo(
        () => (catalogQuery.data ? buildRolePermissionView(role, rolesByName, index) : undefined),
        [catalogQuery.data, index, role, rolesByName],
    );
    const ruleCollection = useMemo(() => collectRoleRuleRows(role, rolesByName), [role, rolesByName]);

    useEffect(() => {
        draftRef.current = draft;
    }, [draft]);

    useEffect(() => {
        if (!permissionView) {
            return;
        }
        const serverSignature = permissionView.signature;
        const previousSignature = appliedSignature.current;
        if (previousSignature === serverSignature) {
            return;
        }

        appliedSignature.current = serverSignature;
        if (previousSignature === undefined) {
            setDraft(cloneRolePermissionDraft(permissionView.draft));
            setSavedSignature(undefined);
            return;
        }

        const currentSignature = permissionDraftSignature(draftRef.current);
        if (currentSignature === previousSignature || currentSignature === serverSignature) {
            setDraft(cloneRolePermissionDraft(permissionView.draft));
            setSavedSignature(undefined);
            setConflict(false);
            return;
        }

        setConflict(true);
    }, [permissionView]);

    const currentSignature = permissionDraftSignature(draft);
    const dirty = Boolean(
        permissionView && !sameRolePermissionDraft(draft, permissionView.draft) && currentSignature !== savedSignature,
    );
    const canManagePermissions = Boolean(
        canUpdate && !isBuiltinRole(role) && permissionView && !permissionView.hasWildcard,
    );
    const editable = canManagePermissions && !updateRole.isPending;
    const counts = permissionView ? countRolePermissionDraft(index, draft) : undefined;
    const workspaceAccess = useMemo(() => buildRoleWorkspaceAccess(index, draft), [draft, index]);
    const workspaceCount = workspaceAccess.filter((workspace) => workspace.accessible).length;

    useUnsavedChangesWarning(dirty);

    useEffect(() => {
        onDirtyChange(dirty);
    }, [dirty, onDirtyChange]);

    useEffect(() => () => onDirtyChange(false), [onDirtyChange]);

    const discard = () => {
        if (!permissionView) {
            return;
        }
        setDraft(cloneRolePermissionDraft(permissionView.draft));
        setSavedSignature(undefined);
        setConflict(false);
    };

    const loadLatest = () => {
        if (!permissionView) {
            return;
        }
        setDraft(cloneRolePermissionDraft(permissionView.draft));
        setSavedSignature(undefined);
        setConflict(false);
    };

    const savePermissions = async () => {
        if (!permissionView) {
            return;
        }
        const submittedSignature = permissionDraftSignature(draft);
        try {
            await updateRole.mutateAsync({
                name: role.metadata.name,
                role: buildRolePermissionPayload(role, index, draft),
                successMessage: "角色权限已保存。",
            });
            setSavedSignature(appliedSignature.current === submittedSignature ? undefined : submittedSignature);
        } catch {
            return;
        }
    };

    const items: TabsProps["items"] = [
        {
            children: (
                <TabBody conflict={conflict} onLoadLatest={loadLatest}>
                    {catalogQuery.isPending ? (
                        <TabSkeleton />
                    ) : catalogQuery.error ? (
                        <CatalogError error={catalogQuery.error} onRetry={() => void catalogQuery.refetch()} />
                    ) : (
                        <RoleMenuPermissions
                            allowedPermissions={allowedPermissions}
                            draft={draft}
                            editable={editable}
                            index={index}
                            onChange={setDraft}
                        />
                    )}
                </TabBody>
            ),
            key: "menu",
            label: "菜单权限",
        },
        {
            children: (
                <TabBody conflict={conflict} onLoadLatest={loadLatest}>
                    {catalogQuery.isPending ? (
                        <TabSkeleton />
                    ) : catalogQuery.error ? (
                        <CatalogError error={catalogQuery.error} onRetry={() => void catalogQuery.refetch()} />
                    ) : (
                        <RoleDataPermissions
                            collection={ruleCollection}
                            index={index}
                            onConfigureOperations={editable ? () => setActiveTab("operation") : undefined}
                        />
                    )}
                </TabBody>
            ),
            key: "data",
            label: "数据权限",
        },
        {
            children: (
                <TabBody conflict={conflict} onLoadLatest={loadLatest}>
                    {catalogQuery.isPending ? (
                        <TabSkeleton />
                    ) : catalogQuery.error ? (
                        <CatalogError error={catalogQuery.error} onRetry={() => void catalogQuery.refetch()} />
                    ) : (
                        <RoleOperationPermissions
                            allowedPermissions={allowedPermissions}
                            draft={draft}
                            editable={editable}
                            index={index}
                            onChange={setDraft}
                        />
                    )}
                </TabBody>
            ),
            key: "operation",
            label: "操作权限",
        },
        {
            children: (
                <TabBody conflict={conflict} onLoadLatest={loadLatest}>
                    {catalogQuery.isPending ? (
                        <TabSkeleton />
                    ) : catalogQuery.error ? (
                        <CatalogError error={catalogQuery.error} onRetry={() => void catalogQuery.refetch()} />
                    ) : (
                        <RoleAccessPermissions
                            onConfigureMenus={editable ? () => setActiveTab("menu") : undefined}
                            workspaces={workspaceAccess}
                        />
                    )}
                </TabBody>
            ),
            key: "access",
            label: "访问权限",
        },
    ];

    return (
        <ProCard
            className="min-w-0 overflow-hidden !rounded-[24px]"
            headerBordered
            style={{
                background: token.colorBgContainer,
                borderColor: token.colorBorderSecondary,
                boxShadow: token.boxShadowTertiary,
            }}
            styles={{
                body: { padding: 0 },
                header: { padding: "20px 24px" },
                title: {
                    display: "block",
                    overflow: "visible",
                    textOverflow: "clip",
                    whiteSpace: "normal",
                    width: "100%",
                },
            }}
            title={
                <RoleSummary
                    canDelete={canDelete}
                    canEdit={canUpdate}
                    canSave={canManagePermissions}
                    dirty={dirty}
                    menuCount={counts?.menuCount}
                    menuTotal={catalogQuery.data ? index.menus.length : undefined}
                    onDelete={() => onDelete(role)}
                    onDiscard={discard}
                    onEdit={() => onEditRole(role)}
                    onRefresh={onRefresh}
                    onSave={() => void savePermissions()}
                    operationCount={counts?.operationCount}
                    operationTotal={catalogQuery.data ? index.operationCount : undefined}
                    refreshDisabled={dirty || updateRole.isPending}
                    role={role}
                    ruleCount={ruleCollection.rows.length}
                    saving={updateRole.isPending}
                    workspaceCount={workspaceCount}
                    workspaceTotal={workspaceAccess.length}
                />
            }
        >
            <div className="px-4 pt-5 pb-6 sm:px-6 sm:pt-6">
                <Tabs activeKey={activeTab} items={items} onChange={setActiveTab} />
            </div>
        </ProCard>
    );
}

type TabBodyProps = {
    children: React.ReactNode;
    conflict: boolean;
    onLoadLatest: () => void;
};

function TabBody({ children, conflict, onLoadLatest }: TabBodyProps) {
    return (
        <div className="flex min-h-[320px] flex-col gap-5">
            {conflict ? (
                <Alert
                    action={<Button onClick={onLoadLatest}>加载最新</Button>}
                    description="服务器上的角色权限已更新。当前草稿已保留，继续保存会覆盖最新配置；如需采用服务器版本，请加载最新。"
                    message="存在并发修改"
                    showIcon
                    type="warning"
                />
            ) : null}
            {children}
        </div>
    );
}

function TabSkeleton() {
    return <Skeleton active paragraph={{ rows: 8 }} title={false} />;
}

function CatalogError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
    return (
        <Alert
            action={
                <Button onClick={onRetry} size="small">
                    重试
                </Button>
            }
            description={consoleErrorMessage(error)}
            message="权限目录加载失败"
            showIcon
            type="error"
        />
    );
}
