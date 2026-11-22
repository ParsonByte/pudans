"use client";

import { type WorkspaceMenu, apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Button, type TableProps } from "antd";

import { ConsoleTable } from "../../../components/console-table";

type PermissionRow = WorkspaceMenu & {
    rowKey: string;
    workspace: string;
};

export function PermissionsView() {
    const catalogQuery = useQuery({
        queryKey: ["console", "permissions"],
        queryFn: async () => (await apiClient.permissionCatalog.getPermissionCatalog()).data,
    });
    const rows: PermissionRow[] = (catalogQuery.data?.workspaces ?? []).flatMap((workspace) =>
        (workspace.menus ?? []).map((menu) => ({
            ...menu,
            rowKey: `${workspace.id ?? workspace.name ?? "workspace"}:${menu.key ?? menu.component ?? "menu"}`,
            workspace: workspace.name ?? workspace.id ?? "—",
        })),
    );

    const columns: TableProps<PermissionRow>["columns"] = [
        { dataIndex: "workspace", key: "workspace", title: "工作区" },
        { dataIndex: "label", key: "label", title: "菜单" },
        { dataIndex: "component", key: "component", render: (value: string) => value ?? "—", title: "视图" },
        {
            key: "group",
            render: (_, record) => [record.group, record.parent].filter(Boolean).join(" / ") || "—",
            title: "分组 / 父级",
        },
        {
            key: "view",
            render: (_, record) => record.view?.permission ?? "—",
            title: "查看权限",
        },
        {
            key: "manage",
            render: (_, record) => record.manage?.permission ?? "—",
            title: "管理权限",
        },
        {
            key: "operations",
            render: (_, record) => record.operations?.length ?? 0,
            title: "操作数",
        },
    ];

    return (
        <ConsoleTable
            columns={columns}
            data={rows}
            error={catalogQuery.error}
            extra={
                <Button onClick={() => void catalogQuery.refetch()} size="small">
                    刷新
                </Button>
            }
            loading={catalogQuery.isPending}
            onRetry={() => void catalogQuery.refetch()}
            pagination={false}
            rowKey={(record) => record.rowKey}
            title="权限目录"
        />
    );
}
