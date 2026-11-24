"use client";

import { useMemo } from "react";

import { RefreshIcon } from "tdesign-icons-react";
import { Breadcrumb, Button, Card, Skeleton } from "tdesign-react";

import { useQueryParams } from "@/hooks/utils/use-query-params";

import { consoleErrorMessage } from "../../../composables/_errors";
import { IdentityLoadState } from "../components/identity-load-state";
import { usePermissionCatalogQuery } from "../composables/_permission-catalog";
import { buildRoleCatalogIndex } from "../roles/composables/_role-permissions";
import { PermissionDirectoryPanel } from "./components/permission-directory-panel";
import { WorkspaceListPanel } from "./components/workspace-list-panel";
import {
    buildPermissionWorkspaceGroups,
    localizeDisplayText,
    menuDisplayLabel,
} from "./composables/_permission-directory";

const { BreadcrumbItem } = Breadcrumb;

export function PermissionsView() {
    const { searchParams, setParam } = useQueryParams();
    const catalogQuery = usePermissionCatalogQuery();
    const catalogIndex = useMemo(() => buildRoleCatalogIndex(catalogQuery.data), [catalogQuery.data]);
    const entries = useMemo(
        () =>
            catalogIndex.menus.map((entry) => ({
                ...entry,
                menu: {
                    ...entry.menu,
                    description: localizeDisplayText(entry.menu.description),
                    label: menuDisplayLabel(entry.menu, catalogIndex.resourceLabels),
                },
                operations: entry.operations.map((operation) => ({
                    ...operation,
                    description: localizeDisplayText(operation.description),
                    label: localizeDisplayText(operation.label),
                })),
            })),
        [catalogIndex],
    );
    const groups = useMemo(() => buildPermissionWorkspaceGroups(entries), [entries]);
    const requestedWorkspace = searchParams.get("permissionWorkspace") ?? undefined;
    const selectedGroup = useMemo(
        () => groups.find((group) => group.key === requestedWorkspace) ?? groups[0],
        [groups, requestedWorkspace],
    );

    const selectWorkspace = (workspaceKey: string) => {
        if (workspaceKey !== selectedGroup?.key) {
            setParam("permissionWorkspace", workspaceKey);
        }
    };

    if (catalogQuery.isPending) {
        return (
            <div className="flex w-full min-w-0 flex-col gap-4">
                <PermissionsBreadcrumb />
                <Card bordered={false} shadow={false}>
                    <Skeleton animation="gradient" theme="paragraph" />
                </Card>
            </div>
        );
    }

    if (catalogQuery.error) {
        return (
            <div className="flex w-full min-w-0 flex-col gap-4">
                <PermissionsBreadcrumb />
                <IdentityLoadState
                    action={
                        <Button onClick={() => void catalogQuery.refetch()} theme="primary">
                            重试
                        </Button>
                    }
                    description={consoleErrorMessage(catalogQuery.error)}
                    title="权限目录加载失败"
                />
            </div>
        );
    }

    if (groups.length === 0) {
        return (
            <div className="flex w-full min-w-0 flex-col gap-4">
                <PermissionsBreadcrumb />
                <IdentityLoadState
                    action={
                        <Button icon={<RefreshIcon />} onClick={() => void catalogQuery.refetch()} theme="primary">
                            刷新目录
                        </Button>
                    }
                    title="权限目录中还没有可展示的菜单"
                    type="empty"
                />
            </div>
        );
    }

    return (
        <div className="flex w-full min-w-0 flex-col gap-4">
            <PermissionsBreadcrumb />
            <Card bordered={false} bodyClassName="!p-0" className="min-w-0 overflow-hidden" shadow={false}>
                <div className="flex min-w-0 flex-col lg:flex-row">
                    <div className="w-full shrink-0 border-b border-[var(--td-component-border)] p-4 lg:w-[232px] lg:border-r lg:border-b-0 lg:p-6">
                        <WorkspaceListPanel
                            groups={groups}
                            onSelect={selectWorkspace}
                            selectedKey={selectedGroup?.key}
                        />
                    </div>
                    <div className="min-w-0 flex-1">
                        {selectedGroup ? (
                            <PermissionDirectoryPanel
                                group={selectedGroup}
                                key={selectedGroup.key}
                                onRefresh={() => void catalogQuery.refetch()}
                                refreshing={catalogQuery.isFetching}
                            />
                        ) : null}
                    </div>
                </div>
            </Card>
        </div>
    );
}

function PermissionsBreadcrumb() {
    return (
        <Breadcrumb>
            <BreadcrumbItem>身份与权限</BreadcrumbItem>
            <BreadcrumbItem>权限目录</BreadcrumbItem>
        </Breadcrumb>
    );
}
