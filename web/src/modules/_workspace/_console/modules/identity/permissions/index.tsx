"use client";

import { useMemo } from "react";

import type { WorkspaceMenuOperation } from "@pudans-dev/api-client";
import { RefreshIcon, TreeListIcon } from "tdesign-icons-react";
import { Button, Card, Skeleton, Table, Tag } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { MenuIcon } from "@/components/ui";

import { CopyableIdentifier } from "../../../components/copyable-identifier";
import { consoleErrorMessage } from "../../../composables/_errors";
import { formatCount } from "../../../composables/_format";
import { IdentityLoadState } from "../components/identity-load-state";
import { type IdentityOverviewMetric, IdentityPageOverview } from "../components/identity-page-overview";
import { usePermissionCatalogQuery } from "../composables/_permission-catalog";
import { RolePermissionBrowser } from "../roles/components/role-permission-browser";
import { type RoleMenuEntry, buildRoleCatalogIndex, operationTypeLabel } from "../roles/composables/_role-permissions";

const CJK_CHARACTER = /[\u3400-\u9fff]/;

const DISPLAY_TEXT_REPLACEMENTS: ReadonlyArray<readonly [RegExp, string]> = [[/\s*Webhook\s*/gi, "回调"]];

const WORKSPACE_LABELS: Record<string, string> = {
    console: "管理控制台",
    default: "个人工作区",
};

type DirectoryRow = RoleMenuEntry & {
    __rowKey: string;
};

export function PermissionsView() {
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
                workspace: {
                    ...entry.workspace,
                    name: workspaceDisplayName(entry.workspace),
                },
            })),
        [catalogIndex],
    );
    const stats = useMemo(() => directoryStats(entries), [entries]);
    const overviewMetrics: IdentityOverviewMetric[] = [
        { label: "工作区", value: formatCount(stats.workspaceCount) },
        { label: "菜单", value: formatCount(stats.menuCount) },
        { label: "操作", value: formatCount(stats.operationCount) },
        { label: "公开菜单", value: formatCount(stats.publicMenuCount) },
    ];

    if (catalogQuery.isPending) {
        return <PermissionDirectorySkeleton />;
    }

    if (catalogQuery.error) {
        return (
            <IdentityLoadState
                action={
                    <Button onClick={() => void catalogQuery.refetch()} theme="primary">
                        重试
                    </Button>
                }
                description={consoleErrorMessage(catalogQuery.error)}
                title="权限目录加载失败"
            />
        );
    }

    if (entries.length === 0) {
        return (
            <IdentityLoadState
                action={
                    <Button icon={<RefreshIcon />} onClick={() => void catalogQuery.refetch()} theme="primary">
                        刷新目录
                    </Button>
                }
                title="权限目录中还没有可展示的菜单"
                type="empty"
            />
        );
    }

    return (
        <div className="flex min-w-0 flex-col gap-4">
            <IdentityPageOverview
                actions={
                    <Button
                        icon={<RefreshIcon />}
                        loading={catalogQuery.isFetching}
                        onClick={() => void catalogQuery.refetch()}
                        variant="outline"
                    >
                        刷新
                    </Button>
                }
                icon={<TreeListIcon />}
                metrics={overviewMetrics}
                title="权限目录"
            />

            <RolePermissionBrowser
                emptyDescription="没有匹配的权限资源"
                entries={entries}
                matchesEntry={matchesEntry}
                renderGroup={(groupEntries) => <PermissionDirectoryTable entries={groupEntries} />}
                summary={workspaceSummary}
                toolbarPlaceholder="搜索工作区、菜单、分组或权限标识"
            />
        </div>
    );
}

type PermissionDirectoryTableProps = {
    entries: RoleMenuEntry[];
};

function PermissionDirectoryTable({ entries }: PermissionDirectoryTableProps) {
    const rows: DirectoryRow[] = entries.map((entry) => ({
        ...entry,
        __rowKey: `${entry.workspace.id ?? entry.workspace.name ?? "workspace"}:${entry.menu.key ?? "menu"}`,
    }));
    const columns: NonNullable<TableProps<DirectoryRow>["columns"]> = [
        {
            cell: ({ row }) => <MenuIdentity entry={row} />,
            colKey: "menu",
            title: "菜单",
        },
        {
            cell: ({ row }) => <MenuLocation entry={row} />,
            colKey: "location",
            title: "分组 / 父级",
            width: 180,
        },
        {
            cell: ({ row }) => <ViewPermission entry={row} />,
            colKey: "view",
            title: "访问权限",
            width: 130,
        },
        {
            cell: ({ row }) => <ManagePermission entry={row} />,
            colKey: "manage",
            title: "管理权限",
            width: 150,
        },
        {
            cell: ({ row }) => <OperationSummary operations={row.operations} />,
            colKey: "operations",
            title: "操作权限",
            width: 160,
        },
    ];

    return (
        <Table<DirectoryRow>
            columns={columns}
            data={rows}
            expandedRow={({ row }) => <MenuPermissionDetails entry={row} />}
            rowKey="__rowKey"
            tableLayout="fixed"
        />
    );
}

function MenuIdentity({ entry }: { entry: RoleMenuEntry }) {
    return (
        <div className="flex min-w-0 items-start gap-2.5">
            <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-[var(--td-bg-color-component)] text-[var(--td-text-color-secondary)]">
                <MenuIcon icon={entry.menu.icon} size={16} />
            </span>
            <div className="min-w-0">
                <div className="flex min-w-0 flex-wrap items-center gap-1.5">
                    <span className="truncate text-sm font-medium">{entry.menu.label || "未命名菜单"}</span>
                    {entry.menu.publicMenu ? (
                        <Tag theme="primary" variant="light">
                            公开
                        </Tag>
                    ) : null}
                </div>
            </div>
        </div>
    );
}

function MenuLocation({ entry }: { entry: RoleMenuEntry }) {
    return (
        <div className="min-w-0">
            <div className="truncate text-sm">{entry.menu.group || "未分组"}</div>
            <div className="mt-0.5 truncate text-xs text-[var(--td-text-color-placeholder)]">
                {entry.menu.parent ? `父级：${entry.menu.parent}` : "无父级"}
            </div>
        </div>
    );
}

function ViewPermission({ entry }: { entry: RoleMenuEntry }) {
    if (entry.menu.publicMenu) {
        return (
            <Tag theme="primary" variant="light">
                公开访问
            </Tag>
        );
    }
    if (!entry.view?.permission) {
        return <span className="text-xs text-[var(--td-text-color-placeholder)]">未配置</span>;
    }

    return (
        <Tag theme="primary" variant="light">
            查看
        </Tag>
    );
}

function ManagePermission({ entry }: { entry: RoleMenuEntry }) {
    if (!entry.manage?.permission) {
        return <span className="text-xs text-[var(--td-text-color-placeholder)]">未配置</span>;
    }

    return (
        <Tag theme="warning" variant="light">
            管理
        </Tag>
    );
}

function OperationSummary({ operations }: { operations: WorkspaceMenuOperation[] }) {
    if (operations.length === 0) {
        return <span className="text-xs text-[var(--td-text-color-placeholder)]">未配置</span>;
    }

    return (
        <Tag theme="primary" variant="light">
            共 {operations.length} 项
        </Tag>
    );
}

function MenuPermissionDetails({ entry }: { entry: RoleMenuEntry }) {
    return (
        <div className="flex min-w-0 flex-col gap-4">
            <div>
                <div className="mb-2 text-xs font-medium text-[var(--td-text-color-secondary)]">技术标识</div>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
                    <TechnicalReference emptyText="未配置" label="菜单标识" value={entry.menu.key} />
                    <TechnicalReference emptyText="未配置" label="页面组件" value={entry.menu.component} />
                    <TechnicalReference
                        emptyText={entry.menu.publicMenu ? "公开访问无需标识" : "未配置"}
                        label="访问标识"
                        value={entry.view?.permission}
                    />
                    <TechnicalReference emptyText="未配置" label="管理标识" value={entry.manage?.permission} />
                </div>
            </div>

            <OperationPermissionDetails operations={entry.operations} />
        </div>
    );
}

type TechnicalReferenceProps = {
    emptyText: string;
    label: string;
    value?: null | string;
};

function TechnicalReference({ emptyText, label, value }: TechnicalReferenceProps) {
    return (
        <div className="min-w-0 rounded-xl bg-[var(--td-bg-color-page)] px-3 py-2">
            <div className="text-[11px] text-[var(--td-text-color-placeholder)]">{label}</div>
            <div className="mt-1">
                <CopyableIdentifier className="text-xs" emptyText={emptyText} value={value} />
            </div>
        </div>
    );
}

function OperationPermissionDetails({ operations }: { operations: WorkspaceMenuOperation[] }) {
    if (operations.length === 0) {
        return (
            <div className="rounded-xl border border-[var(--td-component-border)] px-4 py-6 text-center text-xs text-[var(--td-text-color-placeholder)]">
                该菜单没有独立操作权限
            </div>
        );
    }

    return (
        <div className="overflow-hidden rounded-xl border border-[var(--td-component-border)]">
            <div className="flex flex-wrap items-center justify-between gap-2 bg-[var(--td-bg-color-page)] px-3 py-2">
                <span className="text-xs font-medium">操作明细</span>
                <span className="text-[11px] text-[var(--td-text-color-placeholder)]">共 {operations.length} 项</span>
            </div>
            <div>
                {operations.map((operation, index) => (
                    <OperationDetail
                        key={`${operation.key ?? operation.permission ?? index}`}
                        operation={operation}
                        showDivider={index > 0}
                    />
                ))}
            </div>
        </div>
    );
}

function OperationDetail({ operation, showDivider }: { operation: WorkspaceMenuOperation; showDivider: boolean }) {
    return (
        <div
            className={`flex flex-col gap-2 px-3 py-2.5 sm:flex-row sm:items-start sm:justify-between ${
                showDivider ? "border-t border-[var(--td-component-border)]" : ""
            }`}
        >
            <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-1.5">
                    <span className="text-sm font-medium">{operation.label || "未命名操作"}</span>
                    <Tag variant="light">{operationTypeLabel(operation.type)}</Tag>
                </div>
            </div>
            <div className="shrink-0">
                <CopyableIdentifier emptyText="未配置" value={operation.permission} />
            </div>
        </div>
    );
}

function PermissionDirectorySkeleton() {
    return (
        <div className="flex flex-col gap-4">
            <Card>
                <Skeleton animation="gradient" theme="paragraph" />
            </Card>
            <Card>
                <Skeleton animation="gradient" theme="article" />
            </Card>
        </div>
    );
}

function directoryStats(entries: RoleMenuEntry[]) {
    return {
        menuCount: entries.length,
        operationCount: entries.reduce((total, entry) => total + entry.operations.length, 0),
        publicMenuCount: entries.filter((entry) => entry.menu.publicMenu).length,
        workspaceCount: new Set(entries.map((entry) => entry.workspace.id ?? entry.workspace.name)).size,
    };
}

function workspaceSummary(entries: RoleMenuEntry[]): string {
    const operationCount = entries.reduce((total, entry) => total + entry.operations.length, 0);
    return `${entries.length} 个菜单 · ${operationCount} 个操作`;
}

function menuDisplayLabel(menu: RoleMenuEntry["menu"], resourceLabels: Record<string, string>): string {
    if (menu.label && CJK_CHARACTER.test(menu.label)) {
        return menu.label;
    }

    const resourceLabel = menu.component ? resourceLabels[menu.component] : undefined;
    if (resourceLabel && CJK_CHARACTER.test(resourceLabel)) {
        return resourceLabel;
    }

    return menu.label || "未命名菜单";
}

function workspaceDisplayName(workspace: RoleMenuEntry["workspace"]): string {
    const override = WORKSPACE_LABELS[workspace.id ?? ""];
    if (override) {
        return override;
    }
    if (workspace.name && CJK_CHARACTER.test(workspace.name)) {
        return workspace.name;
    }
    if (workspace.plan && CJK_CHARACTER.test(workspace.plan)) {
        return workspace.plan;
    }
    return workspace.name || workspace.slug || "未命名工作区";
}

function localizeDisplayText(text?: string): string {
    if (!text) {
        return "";
    }
    return DISPLAY_TEXT_REPLACEMENTS.reduce(
        (localized, [pattern, replacement]) => localized.replace(pattern, replacement),
        text,
    );
}

function matchesEntry(entry: RoleMenuEntry, query: string): boolean {
    return [
        entry.workspace.name,
        entry.workspace.slug,
        entry.workspace.plan,
        entry.menu.label,
        entry.menu.description,
        entry.menu.key,
        entry.menu.component,
        entry.menu.group,
        entry.menu.parent,
        entry.view?.permission,
        entry.manage?.permission,
        ...entry.operations.flatMap((operation) => [
            operation.label,
            operation.description,
            operation.key,
            operation.permission,
        ]),
    ]
        .filter(Boolean)
        .join(" ")
        .toLocaleLowerCase()
        .includes(query);
}
