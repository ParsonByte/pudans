"use client";

import type { ReactNode } from "react";
import { useMemo, useState } from "react";

import type { WorkspaceMenuOperation } from "@pudans-dev/api-client";
import { BrowseIcon, ControlPlatformIcon, RefreshIcon, SearchIcon, ViewListIcon } from "tdesign-icons-react";
import { Button, Empty, Input, Table, Tag } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { MenuIcon, useDesignTokens } from "@/components/ui";
import { useMediaQuery } from "@/hooks/ui/use-media-query";

import { type RoleMenuEntry, operationTypeLabel } from "../../roles/composables/_role-permissions";
import {
    type PermissionWorkspaceGroup,
    matchesDirectoryEntry,
    workspaceDescription,
} from "../composables/_permission-directory";

type PermissionDirectoryPanelProps = {
    group: PermissionWorkspaceGroup;
    onRefresh: () => void;
    refreshing: boolean;
};

type DirectoryRow = RoleMenuEntry & {
    __rowKey: string;
};

export function PermissionDirectoryPanel({ group, onRefresh, refreshing }: PermissionDirectoryPanelProps) {
    const tokens = useDesignTokens();
    const showPermissionLevels = useMediaQuery("(min-width: 1280px)");
    const [keyword, setKeyword] = useState("");
    const description = workspaceDescription(group.workspace);
    const entries = useMemo(() => {
        const query = keyword.trim().toLocaleLowerCase();
        return query ? group.entries.filter((entry) => matchesDirectoryEntry(entry, query)) : group.entries;
    }, [group.entries, keyword]);
    const rows: DirectoryRow[] = entries.map((entry) => ({
        ...entry,
        __rowKey: entry.menu.key ?? entry.menu.component ?? entry.menu.label ?? "menu",
    }));
    const columns: NonNullable<TableProps<DirectoryRow>["columns"]> = [
        {
            cell: ({ row }) => <MenuIdentity compact={!showPermissionLevels} entry={row} />,
            colKey: "menu",
            title: "菜单",
        },
        ...(showPermissionLevels
            ? [
                  {
                      cell: ({ row }: { row: DirectoryRow }) => <ViewPermission entry={row} />,
                      colKey: "view",
                      title: "访问权限",
                      width: 92,
                  },
                  {
                      cell: ({ row }: { row: DirectoryRow }) => <ManagePermission entry={row} />,
                      colKey: "manage",
                      title: "管理权限",
                      width: 92,
                  },
                  {
                      cell: ({ row }: { row: DirectoryRow }) => <OperationSummary operations={row.operations} />,
                      colKey: "operations",
                      title: "操作权限",
                      width: 96,
                  },
              ]
            : []),
    ];

    return (
        <div className="min-w-0">
            <div className="border-b border-[var(--td-component-border)] px-4 py-5 lg:px-6">
                <div className="flex min-w-0 flex-wrap items-start justify-between gap-4">
                    <div className="flex min-w-60 flex-1 items-start gap-3">
                        <span
                            className="flex size-10 shrink-0 items-center justify-center rounded-xl"
                            style={{ background: tokens.colorPrimaryBg, color: tokens.colorPrimary }}
                        >
                            <MenuIcon icon={group.workspace.icon} size={19} />
                        </span>
                        <div className="min-w-0">
                            <h2 className="truncate text-lg font-semibold tracking-tight sm:text-xl">{group.name}</h2>
                            {description ? (
                                <p className="mt-2 line-clamp-2 max-w-xl text-xs text-[var(--td-text-color-secondary)]">
                                    {description}
                                </p>
                            ) : null}
                        </div>
                    </div>
                    <Button
                        aria-label="刷新权限目录"
                        icon={<RefreshIcon />}
                        loading={refreshing}
                        onClick={onRefresh}
                        shape="square"
                        variant="outline"
                    />
                </div>

                <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 md:pl-[52px]">
                    <DirectoryMetric icon={<ViewListIcon />} label="菜单" value={group.menuCount} />
                    <DirectoryMetric icon={<ControlPlatformIcon />} label="操作" value={group.operationCount} />
                    <DirectoryMetric icon={<BrowseIcon />} label="公开菜单" value={group.publicMenuCount} />
                </div>
            </div>

            <div className="px-4 pt-5 pb-6 lg:px-6">
                <Input
                    className="w-full"
                    clearable
                    onChange={(value) => setKeyword(String(value))}
                    placeholder="搜索菜单或操作"
                    prefixIcon={<SearchIcon />}
                    value={keyword}
                />
                <div className="mt-4 min-w-0 overflow-x-auto">
                    <Table<DirectoryRow>
                        columns={columns}
                        data={rows}
                        empty={<Empty description={keyword ? "没有匹配的菜单" : "该工作区还没有菜单"} />}
                        expandedRow={({ row }) => <MenuPermissionDetails entry={row} />}
                        rowKey="__rowKey"
                        tableLayout="fixed"
                    />
                </div>
            </div>
        </div>
    );
}

function MenuIdentity({ compact, entry }: { compact: boolean; entry: RoleMenuEntry }) {
    return (
        <div className="flex min-w-0 items-start gap-2.5">
            <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-[var(--td-bg-color-component)] text-[var(--td-text-color-secondary)]">
                <MenuIcon icon={entry.menu.icon} size={16} />
            </span>
            <div className="min-w-0">
                <div className="flex min-w-0 flex-wrap items-center gap-1.5">
                    <span className="truncate text-sm font-medium">{entry.menu.label || "未命名菜单"}</span>
                    {!compact && entry.menu.publicMenu ? (
                        <Tag theme="primary" variant="light">
                            公开
                        </Tag>
                    ) : null}
                </div>
                {entry.menu.description ? (
                    <p className="mt-0.5 truncate text-xs text-[var(--td-text-color-placeholder)]">
                        {entry.menu.description}
                    </p>
                ) : null}
                {compact ? (
                    <div className="mt-1 flex flex-wrap items-center gap-1">
                        <CompactPermissions entry={entry} />
                        <CompactOperations operations={entry.operations} />
                    </div>
                ) : null}
            </div>
        </div>
    );
}

function CompactOperations({ operations }: { operations: WorkspaceMenuOperation[] }) {
    if (operations.length === 0) {
        return <span className="text-[11px] text-[var(--td-text-color-placeholder)]">无操作</span>;
    }

    return (
        <Tag theme="primary" variant="light">
            共 {operations.length} 项
        </Tag>
    );
}

function CompactPermissions({ entry }: { entry: RoleMenuEntry }) {
    return (
        <>
            {entry.menu.publicMenu ? (
                <Tag theme="primary" variant="light">
                    公开
                </Tag>
            ) : null}
            {!entry.menu.publicMenu && entry.view?.permission ? (
                <Tag theme="primary" variant="light">
                    查看
                </Tag>
            ) : null}
            {entry.manage?.permission ? (
                <Tag theme="warning" variant="light">
                    管理
                </Tag>
            ) : null}
        </>
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
        <div className="flex min-w-0 flex-col gap-3">
            {entry.menu.description ? (
                <p className="text-xs text-[var(--td-text-color-secondary)]">{entry.menu.description}</p>
            ) : null}
            {entry.operations.length === 0 ? (
                <div className="rounded-xl border border-[var(--td-component-border)] px-4 py-6 text-center text-xs text-[var(--td-text-color-placeholder)]">
                    该菜单没有独立操作权限
                </div>
            ) : (
                <div className="overflow-hidden rounded-xl border border-[var(--td-component-border)]">
                    <div className="flex flex-wrap items-center justify-between gap-2 bg-[var(--td-bg-color-page)] px-3 py-2">
                        <span className="text-xs font-medium">操作明细</span>
                        <span className="text-[11px] text-[var(--td-text-color-placeholder)]">
                            共 {entry.operations.length} 项
                        </span>
                    </div>
                    {entry.operations.map((operation, index) => (
                        <OperationDetail
                            key={`${operation.key ?? operation.label ?? index}`}
                            operation={operation}
                            showDivider={index > 0}
                        />
                    ))}
                </div>
            )}
        </div>
    );
}

function OperationDetail({ operation, showDivider }: { operation: WorkspaceMenuOperation; showDivider: boolean }) {
    return (
        <div
            className={`flex flex-col gap-1.5 px-3 py-2.5 sm:flex-row sm:items-start sm:justify-between ${
                showDivider ? "border-t border-[var(--td-component-border)]" : ""
            }`}
        >
            <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-1.5">
                    <span className="text-sm font-medium">{operation.label || "未命名操作"}</span>
                    <Tag variant="light">{operationTypeLabel(operation.type)}</Tag>
                </div>
                {operation.description ? (
                    <p className="mt-0.5 text-xs text-[var(--td-text-color-placeholder)]">{operation.description}</p>
                ) : null}
            </div>
        </div>
    );
}

function DirectoryMetric({ icon, label, value }: { icon: ReactNode; label: string; value: number }) {
    const tokens = useDesignTokens();

    return (
        <span className="inline-flex items-center gap-1.5 text-[11px]">
            <span style={{ color: tokens.colorTextTertiary }}>{icon}</span>
            <span style={{ color: tokens.colorTextTertiary }}>{label}</span>
            <span className="font-medium text-[var(--td-text-color-secondary)] tabular-nums">{value}</span>
        </span>
    );
}
