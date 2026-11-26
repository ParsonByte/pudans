"use client";

import { useQuery } from "@tanstack/react-query";
import { RefreshIcon, ServerIcon } from "tdesign-icons-react";
import { Button, Tag, Tooltip } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { ConsoleTable } from "../../../components/console-table";
import { EmptyValue } from "../../../components/empty-value";
import { formatCountOrDash, formatDateTime } from "../../../composables/_format";
import { DansPage } from "../components/page";
import { type Sandbox, listSandboxes } from "../composables/_dans";

const ACTIVE_STATUSES = new Set(["requested", "provisioning", "ready", "running", "snapshotting", "resuming"]);
const FAILED_STATUSES = new Set(["failed", "expired", "cancelled"]);

export function AgentSandboxesView() {
    const sandboxesQuery = useQuery({
        queryFn: () => listSandboxes(100),
        queryKey: ["dans", "agent", "sandboxes"],
    });
    const sandboxes = sandboxesQuery.data?.items ?? [];
    const activeCount = sandboxes.filter((sandbox) => ACTIVE_STATUSES.has(sandbox.status ?? "")).length;
    const failedCount = sandboxes.filter((sandbox) => FAILED_STATUSES.has(sandbox.status ?? "")).length;
    const columns: TableProps<Sandbox>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <Tooltip content={row.id} placement="top-left">
                        <span className="truncate font-mono text-xs">{row.id}</span>
                    </Tooltip>
                    <span className="text-xs text-[var(--td-text-color-secondary)]">
                        {row.provider ?? "—"} / {row.template ?? "—"}
                    </span>
                </div>
            ),
            colKey: "id",
            title: "沙箱",
        },
        { cell: ({ row }) => row.ownerId ?? <EmptyValue />, colKey: "ownerId", title: "所属账号" },
        { cell: ({ row }) => row.workspaceId ?? <EmptyValue />, colKey: "workspaceId", title: "工作区" },
        {
            cell: ({ row }) => (
                <Tag theme={FAILED_STATUSES.has(row.status ?? "") ? "danger" : "primary"} variant="light">
                    {row.status ?? "未知"}
                </Tag>
            ),
            colKey: "status",
            title: "状态",
        },
        { cell: ({ row }) => row.region ?? <EmptyValue />, colKey: "region", title: "区域" },
        { cell: ({ row }) => formatDateTime(row.startedAt), colKey: "startedAt", title: "启动时间" },
        { cell: ({ row }) => formatDateTime(row.expiresAt), colKey: "expiresAt", title: "过期时间" },
        {
            cell: ({ row }) =>
                row.errorCode ? (
                    <div className="flex max-w-[240px] flex-col gap-1 text-xs">
                        <span className="font-mono">{row.errorCode}</span>
                        <span className="truncate text-[var(--td-text-color-secondary)]" title={row.errorMessage}>
                            {row.errorMessage ?? "—"}
                        </span>
                    </div>
                ) : (
                    <EmptyValue />
                ),
            colKey: "errorCode",
            title: "异常",
        },
    ];

    return (
        <DansPage
            actions={
                <Button
                    icon={<RefreshIcon />}
                    loading={sandboxesQuery.isFetching}
                    onClick={() => void sandboxesQuery.refetch()}
                    variant="outline"
                >
                    刷新
                </Button>
            }
            description="沙箱实例按 Agent 会话隔离，平台范围展示真实运行状态、资源模板和过期信息。"
            icon={<ServerIcon size={22} />}
            metrics={[
                { label: "沙箱总数", value: formatCountOrDash(sandboxesQuery.data?.items?.length) },
                { label: "运行中", value: formatCountOrDash(activeCount) },
                { label: "失败或过期", value: formatCountOrDash(failedCount) },
            ]}
            section="Agent"
            title="沙箱"
        >
            <ConsoleTable
                columns={columns}
                data={sandboxes}
                error={sandboxesQuery.error}
                loading={sandboxesQuery.isPending}
                onRetry={() => void sandboxesQuery.refetch()}
                rowKey="id"
                title="沙箱实例"
            />
        </DansPage>
    );
}
