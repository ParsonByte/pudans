"use client";

import { useQuery } from "@tanstack/react-query";
import { RefreshIcon, TerminalIcon } from "tdesign-icons-react";
import { Button, Tag, Tooltip } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { ConsoleTable } from "../../../components/console-table";
import { EmptyValue } from "../../../components/empty-value";
import { formatCountOrDash, formatDateTime } from "../../../composables/_format";
import { DansPage } from "../components/page";
import { type AgentSession, getAgentSessionSummary, listAgentSessions } from "../composables/_dans";

export function AgentSessionsView() {
    const sessionsQuery = useQuery({
        queryFn: () => listAgentSessions(100),
        queryKey: ["dans", "agent", "sessions"],
    });
    const summaryQuery = useQuery({
        queryFn: getAgentSessionSummary,
        queryKey: ["dans", "agent", "session-summary"],
    });
    const sessions = sessionsQuery.data?.items ?? [];
    const columns: TableProps<AgentSession>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="truncate font-medium">{row.title ?? "未命名会话"}</span>
                    <Tooltip content={row.id} placement="top-left">
                        <span className="truncate font-mono text-xs text-[var(--td-text-color-secondary)]">
                            {row.id}
                        </span>
                    </Tooltip>
                </div>
            ),
            colKey: "title",
            title: "会话",
        },
        { cell: ({ row }) => row.ownerId ?? <EmptyValue />, colKey: "ownerId", title: "所属账号" },
        { cell: ({ row }) => row.workspaceId ?? <EmptyValue />, colKey: "workspaceId", title: "工作区" },
        {
            cell: ({ row }) => (
                <Tag theme={row.status === "active" ? "success" : "default"} variant="light">
                    {row.status ?? "未设置"}
                </Tag>
            ),
            colKey: "status",
            title: "状态",
        },
        { cell: ({ row }) => formatDateTime(row.lastMessageAt), colKey: "lastMessageAt", title: "最后消息" },
        { cell: ({ row }) => formatDateTime(row.createdAt), colKey: "createdAt", title: "创建时间" },
        { cell: ({ row }) => formatDateTime(row.updatedAt), colKey: "updatedAt", title: "更新时间" },
    ];

    return (
        <DansPage
            actions={
                <Button
                    icon={<RefreshIcon />}
                    loading={sessionsQuery.isFetching || summaryQuery.isFetching}
                    onClick={() => {
                        void sessionsQuery.refetch();
                        void summaryQuery.refetch();
                    }}
                    variant="outline"
                >
                    刷新
                </Button>
            }
            description="平台会话数据用于查看全部账号的消息入口、运行状态和最近活动。运行事件与审批由 Agent 运行接口按会话关联。"
            icon={<TerminalIcon size={22} />}
            metrics={[
                { label: "会话总数", value: formatCountOrDash(summaryQuery.data?.total) },
                { label: "活跃会话", value: formatCountOrDash(summaryQuery.data?.active) },
                { label: "最后消息", value: formatDateTime(summaryQuery.data?.latestMessageAt) },
            ]}
            section="Agent"
            title="会话与运行"
        >
            <ConsoleTable
                columns={columns}
                data={sessions}
                error={sessionsQuery.error}
                loading={sessionsQuery.isPending}
                onRetry={() => void sessionsQuery.refetch()}
                rowKey="id"
                title="会话列表"
            />
        </DansPage>
    );
}
