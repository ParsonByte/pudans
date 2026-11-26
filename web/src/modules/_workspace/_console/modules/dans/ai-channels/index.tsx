"use client";

import { useQuery } from "@tanstack/react-query";
import { DataBaseIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Tag } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { ConsoleTable } from "../../../components/console-table";
import { EmptyValue } from "../../../components/empty-value";
import { formatCountOrDash, formatDateTime } from "../../../composables/_format";
import { DansPage } from "../components/page";
import { type AiChannel, listAiChannels } from "../composables/_dans";

export function AiChannelsView() {
    const channelsQuery = useQuery({
        queryFn: listAiChannels,
        queryKey: ["dans", "ai", "channels"],
    });
    const channels = channelsQuery.data ?? [];
    const credentialCount = channels.filter((channel) => channel.credentialConfigured).length;
    const configuredCount = channels.filter((channel) => channel.status !== "disabled").length;
    const columns: TableProps<AiChannel>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="font-medium">{row.provider ?? <EmptyValue />}</span>
                    <span className="truncate font-mono text-xs text-[var(--td-text-color-secondary)]">{row.id}</span>
                </div>
            ),
            colKey: "provider",
            title: "渠道",
        },
        { cell: ({ row }) => row.protocol ?? <EmptyValue />, colKey: "protocol", title: "协议" },
        {
            cell: ({ row }) =>
                row.baseUrl ? (
                    <span className="block max-w-[260px] truncate font-mono text-xs">{row.baseUrl}</span>
                ) : (
                    <EmptyValue />
                ),
            colKey: "baseUrl",
            title: "接入地址",
        },
        {
            cell: ({ row }) => (
                <Tag theme={row.status === "active" ? "success" : "default"} variant="light">
                    {row.status ?? "未设置"}
                </Tag>
            ),
            colKey: "status",
            title: "状态",
        },
        {
            cell: ({ row }) => (
                <Tag theme={row.credentialConfigured ? "success" : "warning"} variant="light">
                    {row.credentialConfigured ? "已配置" : "未配置"}
                </Tag>
            ),
            colKey: "credentialConfigured",
            title: "凭据",
        },
        {
            cell: ({ row }) => `${row.maxConcurrency ?? "—"} / ${row.rateLimit ?? "—"} / ${row.weight ?? "—"}`,
            colKey: "maxConcurrency",
            title: "并发 / 限流 / 权重",
        },
        {
            cell: ({ row }) => formatDateTime(row.updatedAt),
            colKey: "updatedAt",
            title: "更新时间",
        },
    ];

    return (
        <DansPage
            actions={
                <Button
                    icon={<RefreshIcon />}
                    loading={channelsQuery.isFetching}
                    onClick={() => void channelsQuery.refetch()}
                    variant="outline"
                >
                    刷新
                </Button>
            }
            description="平台渠道凭据只显示配置状态，不返回明文。模型发现、价格和默认模型路由由后续菜单维护。"
            icon={<DataBaseIcon size={22} />}
            metrics={[
                { label: "渠道总数", value: formatCountOrDash(channelsQuery.data?.length) },
                { label: "运行渠道", value: formatCountOrDash(configuredCount) },
                { label: "凭据已配置", value: formatCountOrDash(credentialCount) },
            ]}
            section="AI"
            title="模型渠道"
        >
            <ConsoleTable
                columns={columns}
                data={channels}
                error={channelsQuery.error}
                loading={channelsQuery.isPending}
                onRetry={() => void channelsQuery.refetch()}
                rowKey="id"
                title="渠道列表"
            />
        </DansPage>
    );
}
