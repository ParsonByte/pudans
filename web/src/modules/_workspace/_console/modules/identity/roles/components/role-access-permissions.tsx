"use client";

import { ViewListIcon } from "tdesign-icons-react";
import { Button, Card, Empty, Table, Tag, Tooltip } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { MenuIcon } from "@/components/ui";

import type { RoleMenuEntry, RoleWorkspaceAccess } from "../composables/_role-permissions";

type RoleAccessPermissionsProps = {
    onConfigureMenus?: () => void;
    workspaces: RoleWorkspaceAccess[];
};

type WorkspaceRow = RoleWorkspaceAccess & {
    __rowKey: string;
};

export function RoleAccessPermissions({ onConfigureMenus, workspaces }: RoleAccessPermissionsProps) {
    const rows: WorkspaceRow[] = workspaces.map((workspace) => ({
        ...workspace,
        __rowKey: workspace.workspace.id ?? workspace.workspace.name ?? "workspace",
    }));
    const columns: NonNullable<TableProps<WorkspaceRow>["columns"]> = [
        {
            cell: ({ row }) => <WorkspaceCell workspace={row} />,
            colKey: "workspace",
            title: "工作区",
            width: 170,
        },
        {
            cell: ({ row }) => (
                <Tag theme={row.accessible ? "success" : "default"} variant="light">
                    {row.accessible ? "可访问" : "未授权"}
                </Tag>
            ),
            colKey: "status",
            title: "状态",
            width: 80,
        },
        {
            cell: ({ row }) => <WorkspaceMenuCount workspace={row} />,
            colKey: "menus",
            title: "可见菜单",
            width: 80,
        },
        {
            cell: ({ row }) => <AccessCount total={row.operationCount} value={row.grantedOperationCount} />,
            colKey: "operations",
            title: "可执行操作",
            width: 90,
        },
        {
            cell: ({ row }) => <VisibleMenus entries={row.visibleEntries} />,
            colKey: "visible",
            title: "已授权菜单",
        },
    ];

    return (
        <Card
            actions={
                onConfigureMenus ? (
                    <Button icon={<ViewListIcon />} onClick={onConfigureMenus} size="small" variant="outline">
                        配置菜单
                    </Button>
                ) : null
            }
            header={<div className="text-base font-semibold">工作区访问</div>}
            headerBordered
        >
            {rows.length === 0 ? (
                <Empty description="权限目录中还没有可访问的工作区" />
            ) : (
                <Table<WorkspaceRow> columns={columns} data={rows} rowKey="__rowKey" />
            )}
        </Card>
    );
}

function WorkspaceCell({ workspace }: { workspace: RoleWorkspaceAccess }) {
    const name = workspace.workspace.name ?? workspace.workspace.id ?? "未命名工作区";
    const slug = workspace.workspace.slug ?? workspace.workspace.id;
    const content = (
        <div className="min-w-0">
            <div className="truncate text-sm font-medium">{name}</div>
            {slug ? (
                <div className="truncate font-mono text-[10px] text-[var(--td-text-color-placeholder)]">{slug}</div>
            ) : null}
        </div>
    );

    return (
        <div className="flex min-w-0 items-center gap-2.5">
            <span
                className={`flex size-8 shrink-0 items-center justify-center rounded-lg ${
                    workspace.accessible
                        ? "bg-[var(--td-brand-color-light)] text-[var(--td-brand-color)]"
                        : "bg-[var(--td-bg-color-component)] text-[var(--td-text-color-placeholder)]"
                }`}
            >
                <MenuIcon icon={workspace.workspace.icon} size={16} />
            </span>
            {workspace.workspace.plan ? <Tooltip content={workspace.workspace.plan}>{content}</Tooltip> : content}
        </div>
    );
}

function AccessCount({ total, value }: { total: number; value: number }) {
    return (
        <div className="text-sm font-medium tabular-nums">
            {value}
            <span className="font-normal text-[var(--td-text-color-placeholder)]">/{total}</span>
        </div>
    );
}

function WorkspaceMenuCount({ workspace }: { workspace: RoleWorkspaceAccess }) {
    return <AccessCount total={workspace.menuCount} value={workspace.visibleEntries.length} />;
}

function VisibleMenus({ entries }: { entries: RoleMenuEntry[] }) {
    const visibleEntries = entries.filter((entry) => entry.menu.label || entry.menu.key);
    const labels = visibleEntries.map((entry) => entry.menu.label ?? entry.menu.key ?? "");

    if (labels.length === 0) {
        return <span className="text-xs text-[var(--td-text-color-placeholder)]">未填写</span>;
    }

    const shownEntries = visibleEntries.slice(0, 3);
    const hiddenCount = visibleEntries.length - shownEntries.length;

    return (
        <Tooltip content={labels.join("、")}>
            <div className="flex flex-wrap items-center gap-1">
                {shownEntries.map((entry) => (
                    <Tag key={entry.menu.key ?? entry.menu.label} variant="light">
                        {entry.menu.label ?? entry.menu.key}
                    </Tag>
                ))}
                {hiddenCount > 0 ? <Tag variant="light">+{hiddenCount}</Tag> : null}
            </div>
        </Tooltip>
    );
}
