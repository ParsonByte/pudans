"use client";

import { ProCard } from "@ant-design/pro-components";
import { Icon } from "@iconify/react";
import { Button, Empty, Table, type TableProps, Tag, Tooltip, theme } from "antd";
import { Building2, Menu } from "lucide-react";

import type { RoleMenuEntry, RoleWorkspaceAccess } from "../composables/_role-permissions";

type RoleAccessPermissionsProps = {
    onConfigureMenus?: () => void;
    workspaces: RoleWorkspaceAccess[];
};

export function RoleAccessPermissions({ onConfigureMenus, workspaces }: RoleAccessPermissionsProps) {
    const { token } = theme.useToken();

    const columns: TableProps<RoleWorkspaceAccess>["columns"] = [
        {
            key: "workspace",
            render: (_, workspace) => <WorkspaceCell workspace={workspace} />,
            title: "工作区",
            width: 170,
        },
        {
            key: "status",
            render: (_, workspace) => (
                <Tag className="!m-0" color={workspace.accessible ? "success" : "default"}>
                    {workspace.accessible ? "可访问" : "未授权"}
                </Tag>
            ),
            title: "状态",
            width: 80,
        },
        {
            key: "menus",
            render: (_, workspace) => <WorkspaceMenuCount workspace={workspace} />,
            title: "可见菜单",
            width: 80,
        },
        {
            key: "operations",
            render: (_, workspace) => (
                <AccessCount total={workspace.operationCount} value={workspace.grantedOperationCount} />
            ),
            title: "可执行操作",
            width: 90,
        },
        {
            key: "visible",
            render: (_, workspace) => <VisibleMenus entries={workspace.visibleEntries} />,
            title: "已授权菜单",
        },
    ];

    return (
        <ProCard
            className="!rounded-2xl"
            headerBordered
            styles={{
                body: { padding: 20 },
                header: { paddingBlock: 14, paddingInline: 20 },
                title: {
                    display: "block",
                    overflow: "visible",
                    textOverflow: "clip",
                    whiteSpace: "normal",
                },
            }}
            extra={
                onConfigureMenus ? (
                    <Button icon={<Menu size={14} />} onClick={onConfigureMenus}>
                        配置菜单
                    </Button>
                ) : null
            }
            title={
                <div>
                    <div className="text-base font-semibold">工作区访问</div>
                    <div className="mt-1 text-xs font-normal" style={{ color: token.colorTextSecondary }}>
                        访问范围由菜单可见性自动派生，不维护独立的工作区权限。
                    </div>
                </div>
            }
        >
            {workspaces.length === 0 ? (
                <Empty description="权限目录中还没有可访问的工作区" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
                <Table<RoleWorkspaceAccess>
                    columns={columns}
                    dataSource={workspaces}
                    pagination={false}
                    rowKey={(workspace) => workspace.workspace.id ?? workspace.workspace.name ?? "workspace"}
                    scroll={{ x: 520 }}
                />
            )}
        </ProCard>
    );
}

function WorkspaceCell({ workspace }: { workspace: RoleWorkspaceAccess }) {
    const { token } = theme.useToken();
    const name = workspace.workspace.name ?? workspace.workspace.id ?? "未命名工作区";
    const slug = workspace.workspace.slug ?? workspace.workspace.id;

    return (
        <div className="flex min-w-0 items-center gap-2.5">
            <span
                className="flex size-8 shrink-0 items-center justify-center rounded-lg"
                style={{
                    background: workspace.accessible ? token.colorPrimaryBg : token.colorFillTertiary,
                    color: workspace.accessible ? token.colorPrimary : token.colorTextTertiary,
                }}
            >
                {workspace.workspace.icon?.includes(":") ? (
                    <Icon icon={workspace.workspace.icon} width={16} />
                ) : (
                    <Building2 size={16} />
                )}
            </span>
            <div className="min-w-0">
                <Tooltip title={workspace.workspace.plan}>
                    <div className="truncate text-sm font-medium">{name}</div>
                </Tooltip>
                <div className="truncate font-mono text-[10px]" style={{ color: token.colorTextTertiary }}>
                    {slug}
                </div>
            </div>
        </div>
    );
}

function AccessCount({ hint, total, value }: { hint?: string; total: number; value: number }) {
    const { token } = theme.useToken();

    return (
        <div>
            <div className="text-sm font-medium tabular-nums">
                {value}
                <span className="font-normal" style={{ color: token.colorTextTertiary }}>
                    /{total}
                </span>
            </div>
            {hint ? (
                <div className="text-[10px]" style={{ color: token.colorTextTertiary }}>
                    {hint}
                </div>
            ) : null}
        </div>
    );
}

function WorkspaceMenuCount({ workspace }: { workspace: RoleWorkspaceAccess }) {
    const publicCount = publicMenuCount(workspace.entries);

    return (
        <AccessCount
            hint={publicCount > 0 ? `公开 ${publicCount}` : undefined}
            total={workspace.menuCount}
            value={workspace.visibleEntries.length}
        />
    );
}

function VisibleMenus({ entries }: { entries: RoleMenuEntry[] }) {
    const { token } = theme.useToken();
    const visibleEntries = entries.filter((entry) => entry.menu.label || entry.menu.key);
    const labels = visibleEntries.map((entry) => entry.menu.label ?? entry.menu.key ?? "");

    if (labels.length === 0) {
        return (
            <span className="text-xs" style={{ color: token.colorTextTertiary }}>
                暂无可见菜单
            </span>
        );
    }

    const shownEntries = visibleEntries.slice(0, 3);
    const hiddenCount = visibleEntries.length - shownEntries.length;

    return (
        <Tooltip title={labels.join("、")}>
            <div className="flex flex-wrap items-center gap-1">
                {shownEntries.map((entry) => (
                    <Tag className="!m-0" key={entry.menu.key ?? entry.menu.label}>
                        {entry.menu.label ?? entry.menu.key}
                    </Tag>
                ))}
                {hiddenCount > 0 ? <Tag className="!m-0">+{hiddenCount}</Tag> : null}
            </div>
        </Tooltip>
    );
}

function publicMenuCount(entries: RoleMenuEntry[]): number {
    return entries.filter((entry) => entry.menu.publicMenu).length;
}
