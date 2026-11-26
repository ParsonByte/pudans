"use client";

import { useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { LinkIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Tabs, Tag } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { ConsoleTable } from "../../../components/console-table";
import { EmptyValue } from "../../../components/empty-value";
import { formatCountOrDash, formatDateTime } from "../../../composables/_format";
import { DansPage } from "../components/page";
import {
    type AiChannelModel,
    type AiDefaultModel,
    type AiModel,
    listAiChannelModels,
    listAiDefaultModels,
    listAiModels,
} from "../composables/_dans";

const TABS = [
    { label: "逻辑模型", value: "models" },
    { label: "渠道路由", value: "routes" },
    { label: "默认模型", value: "defaults" },
] as const;

type ModelTab = (typeof TABS)[number]["value"];

export function AiModelsView() {
    const [tab, setTab] = useState<ModelTab>("models");
    const modelsQuery = useQuery({ queryFn: listAiModels, queryKey: ["dans", "ai", "models"] });
    const routesQuery = useQuery({
        queryFn: listAiChannelModels,
        queryKey: ["dans", "ai", "channel-models"],
    });
    const defaultsQuery = useQuery({
        queryFn: listAiDefaultModels,
        queryKey: ["dans", "ai", "default-models"],
    });
    const models = modelsQuery.data ?? [];
    const routes = routesQuery.data ?? [];
    const defaults = defaultsQuery.data ?? [];
    const modelLabel = (id?: string) => {
        const model = models.find((item) => item.id === id);

        return model ? (model.displayName ?? model.logicalName ?? model.id) : (id ?? "—");
    };
    const modelColumns: TableProps<AiModel>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="font-medium">{row.displayName ?? row.logicalName ?? <EmptyValue />}</span>
                    <span className="truncate font-mono text-xs text-[var(--td-text-color-secondary)]">
                        {row.logicalName ?? row.id}
                    </span>
                </div>
            ),
            colKey: "logicalName",
            title: "逻辑模型",
        },
        { cell: ({ row }) => row.description ?? <EmptyValue />, colKey: "description", title: "说明" },
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
            cell: ({ row }) => `${row.contextWindow ?? "—"} / ${row.maxOutputTokens ?? "—"}`,
            colKey: "contextWindow",
            title: "上下文 / 输出",
        },
        { cell: ({ row }) => formatDateTime(row.updatedAt), colKey: "updatedAt", title: "更新时间" },
    ];
    const routeColumns: TableProps<AiChannelModel>["columns"] = [
        {
            cell: ({ row }) => modelLabel(row.modelId),
            colKey: "modelId",
            title: "逻辑模型",
        },
        {
            cell: ({ row }) => <span className="font-mono text-xs">{row.upstreamModelKey ?? "—"}</span>,
            colKey: "upstreamModelKey",
            title: "上游模型",
        },
        {
            cell: ({ row }) => <span className="truncate font-mono text-xs">{row.channelId ?? "—"}</span>,
            colKey: "channelId",
            title: "渠道标识",
        },
        {
            cell: ({ row }) => (
                <Tag theme={row.enabled ? "success" : "default"} variant="light">
                    {row.enabled ? "启用" : "停用"}
                </Tag>
            ),
            colKey: "enabled",
            title: "状态",
        },
        {
            cell: ({ row }) => `${row.priority ?? "—"} / ${row.weight ?? "—"}`,
            colKey: "priority",
            title: "优先级 / 权重",
        },
        { cell: ({ row }) => formatDateTime(row.updatedAt), colKey: "updatedAt", title: "更新时间" },
    ];
    const defaultColumns: TableProps<AiDefaultModel>["columns"] = [
        { cell: ({ row }) => row.capability ?? <EmptyValue />, colKey: "capability", title: "能力" },
        { cell: ({ row }) => modelLabel(row.modelId), colKey: "modelId", title: "默认模型" },
        { cell: ({ row }) => row.workspaceId ?? <EmptyValue />, colKey: "workspaceId", title: "工作区" },
        { cell: ({ row }) => formatDateTime(row.updatedAt), colKey: "updatedAt", title: "更新时间" },
    ];
    const activeQuery = tab === "models" ? modelsQuery : tab === "routes" ? routesQuery : defaultsQuery;
    const refresh = () => {
        void modelsQuery.refetch();
        void routesQuery.refetch();
        void defaultsQuery.refetch();
    };

    return (
        <DansPage
            actions={
                <Button icon={<RefreshIcon />} loading={activeQuery.isFetching} onClick={refresh} variant="outline">
                    刷新
                </Button>
            }
            description="平台逻辑模型定义能力与参数；渠道路由负责映射到真实上游模型；默认模型按能力指定路由入口。"
            icon={<LinkIcon size={22} />}
            metrics={[
                { label: "逻辑模型", value: formatCountOrDash(modelsQuery.data?.length) },
                { label: "渠道路由", value: formatCountOrDash(routesQuery.data?.length) },
                { label: "默认模型", value: formatCountOrDash(defaultsQuery.data?.length) },
            ]}
            section="AI"
            title="模型与路由"
        >
            <Tabs
                list={TABS.map((item) => ({ label: item.label, value: item.value }))}
                onChange={(value) => setTab(String(value) as ModelTab)}
                value={tab}
            />
            {tab === "models" ? (
                <ConsoleTable
                    columns={modelColumns}
                    data={models}
                    error={modelsQuery.error}
                    loading={modelsQuery.isPending}
                    onRetry={() => void modelsQuery.refetch()}
                    rowKey="id"
                    title="逻辑模型"
                />
            ) : tab === "routes" ? (
                <ConsoleTable
                    columns={routeColumns}
                    data={routes}
                    error={routesQuery.error}
                    loading={routesQuery.isPending}
                    onRetry={() => void routesQuery.refetch()}
                    rowKey="id"
                    title="渠道路由"
                />
            ) : (
                <ConsoleTable
                    columns={defaultColumns}
                    data={defaults}
                    error={defaultsQuery.error}
                    loading={defaultsQuery.isPending}
                    onRetry={() => void defaultsQuery.refetch()}
                    rowKey="id"
                    title="默认模型"
                />
            )}
        </DansPage>
    );
}
