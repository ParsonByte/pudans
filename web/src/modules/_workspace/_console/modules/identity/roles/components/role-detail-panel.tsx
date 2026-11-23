"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import type { Role } from "@pudans-dev/api-client";
import { Button, Card, Empty, Skeleton, Tabs } from "tdesign-react";
import type { TabValue } from "tdesign-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
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
    const prompt = useMessagePrompt();
    const catalogQuery = usePermissionCatalogQuery();
    const updateRole = useUpdateRole();
    const [activeTab, setActiveTab] = useState<TabValue>("menu");
    const [draft, setDraft] = useState<RolePermissionDraft>(emptyRolePermissionDraft);
    const [savedSignature, setSavedSignature] = useState<string>();
    const [conflict, setConflict] = useState(false);
    const appliedSignature = useRef<string | undefined>(undefined);
    const draftRef = useRef(draft);
    const conflictNotified = useRef(false);

    const rolesByName = useMemo(() => new Map(roles.map((candidate) => [candidate.metadata.name, candidate])), [roles]);
    const index = useMemo(() => buildRoleCatalogIndex(catalogQuery.data), [catalogQuery.data]);
    const permissionView = useMemo(
        () => (catalogQuery.data ? buildRolePermissionView(role, rolesByName, index) : undefined),
        [catalogQuery.data, index, role, rolesByName],
    );
    const ruleCollection = useMemo(() => collectRoleRuleRows(role, rolesByName), [role, rolesByName]);
    const loadLatest = useCallback(() => {
        if (!permissionView) {
            return;
        }
        setDraft(cloneRolePermissionDraft(permissionView.draft));
        setSavedSignature(undefined);
        setConflict(false);
    }, [permissionView]);

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

    useEffect(() => {
        if (!conflict || conflictNotified.current) {
            return;
        }

        conflictNotified.current = true;
        prompt.warning({
            content: "服务器上的角色权限已更新，当前草稿已保留；继续保存会覆盖最新配置。",
            duration: 0,
            footer: (
                <Button onClick={loadLatest} size="small" variant="outline">
                    加载最新
                </Button>
            ),
            title: "存在并发修改",
        });
    }, [conflict, loadLatest, prompt]);

    useEffect(() => {
        if (!conflict) {
            conflictNotified.current = false;
        }
    }, [conflict]);

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

    function discard() {
        if (!permissionView) {
            return;
        }
        setDraft(cloneRolePermissionDraft(permissionView.draft));
        setSavedSignature(undefined);
        setConflict(false);
    }

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

    const items = [
        {
            label: "菜单权限",
            panel: (
                <TabBody>
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
            value: "menu",
        },
        {
            label: "数据权限",
            panel: (
                <TabBody>
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
            value: "data",
        },
        {
            label: "操作权限",
            panel: (
                <TabBody>
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
            value: "operation",
        },
        {
            label: "访问权限",
            panel: (
                <TabBody>
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
            value: "access",
        },
    ];

    return (
        <Card
            className="min-w-0 overflow-hidden"
            header={
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
            headerBordered
        >
            <div className="px-4 pt-5 pb-6 sm:px-6 sm:pt-6">
                <Tabs list={items} onChange={setActiveTab} value={activeTab} />
            </div>
        </Card>
    );
}

function TabBody({ children }: { children: React.ReactNode }) {
    return <div className="flex min-h-[320px] flex-col gap-5">{children}</div>;
}

function TabSkeleton() {
    return <Skeleton animation="gradient" theme="paragraph" />;
}

function CatalogError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
    return (
        <Empty
            action={
                <Button onClick={onRetry} size="small" variant="outline">
                    重试
                </Button>
            }
            description={consoleErrorMessage(error)}
            title="权限目录加载失败"
            type="fail"
        />
    );
}
