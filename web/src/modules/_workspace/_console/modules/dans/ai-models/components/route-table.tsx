"use client";

import { useMemo } from "react";

import { SearchIcon } from "tdesign-icons-react";
import { Input, Select, Tag } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { ConsoleTable } from "../../../../components/console-table";
import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { formatCount, formatDateTime } from "../../../../composables/_format";
import type { AiChannelModel, AiModel } from "../../composables/_dans";
import { capabilityLabel, modelLabel, modelTypeOf } from "../../composables/_model-meta";

const STATUS_OPTIONS = [
    { label: "全部状态", value: "all" },
    { label: "已启用", value: "enabled" },
    { label: "已停用", value: "disabled" },
];

type RouteTableProps = {
    error?: unknown;
    keyword: string;
    loading: boolean;
    modelId: string;
    models: AiModel[];
    onKeywordChange: (value: string) => void;
    onModelChange: (value: string) => void;
    onRetry: () => void;
    onStatusChange: (value: string) => void;
    routeCounts: Map<string, number>;
    routes: AiChannelModel[];
    status: string;
};

export function RouteTable({
    error,
    keyword,
    loading,
    modelId,
    models,
    onKeywordChange,
    onModelChange,
    onRetry,
    onStatusChange,
    routeCounts,
    routes,
    status,
}: RouteTableProps) {
    const modelsById = useMemo(() => new Map(models.map((model) => [model.id, model])), [models]);
    const modelOptions = useMemo(
        () => [
            { label: "全部模型", value: "all" },
            ...models
                .filter((model) => (routeCounts.get(model.id) ?? 0) > 0)
                .map((model) => ({ label: modelLabel(model), value: model.id }))
                .sort((left, right) => left.label.localeCompare(right.label, "zh-CN")),
        ],
        [models, routeCounts],
    );
    const normalizedKeyword = keyword.trim().toLowerCase();
    const filteredRoutes = useMemo(
        () =>
            routes
                .filter((route) => {
                    if (modelId !== "all" && route.modelId !== modelId) {
                        return false;
                    }
                    if (status === "enabled" && !route.enabled) {
                        return false;
                    }
                    if (status === "disabled" && route.enabled) {
                        return false;
                    }
                    if (!normalizedKeyword) {
                        return true;
                    }
                    const model = route.modelId ? modelsById.get(route.modelId) : undefined;

                    return [modelLabel(model), model?.logicalName, route.upstreamModelKey, route.channelId].some(
                        (value) => value?.toLowerCase().includes(normalizedKeyword),
                    );
                })
                .sort((left, right) => {
                    const priority = (right.priority ?? 0) - (left.priority ?? 0);
                    const weight = (right.weight ?? 0) - (left.weight ?? 0);
                    if (priority !== 0) {
                        return priority;
                    }
                    if (weight !== 0) {
                        return weight;
                    }
                    const leftModel = left.modelId ? modelsById.get(left.modelId) : undefined;
                    const rightModel = right.modelId ? modelsById.get(right.modelId) : undefined;

                    return modelLabel(leftModel).localeCompare(modelLabel(rightModel), "zh-CN");
                }),
        [modelId, modelsById, normalizedKeyword, routes, status],
    );
    const columns: TableProps<AiChannelModel>["columns"] = [
        {
            cell: ({ row }) => {
                const model = row.modelId ? modelsById.get(row.modelId) : undefined;
                const routeCount = row.modelId ? (routeCounts.get(row.modelId) ?? 0) : 0;

                return (
                    <div className="flex min-w-0 flex-col gap-1">
                        <div className="flex min-w-0 flex-wrap items-center gap-2">
                            <span className="truncate font-medium">{modelLabel(model)}</span>
                            {routeCount > 1 ? (
                                <Tag size="small" variant="outline">
                                    共 {formatCount(routeCount)} 条
                                </Tag>
                            ) : null}
                        </div>
                        <CopyableIdentifier
                            className="max-w-full text-[var(--td-text-color-secondary)]"
                            display={
                                <span className="min-w-0 truncate" title={model?.logicalName ?? row.modelId}>
                                    {model?.logicalName ?? row.modelId}
                                </span>
                            }
                            quiet
                            value={model?.logicalName ?? row.modelId}
                        />
                    </div>
                );
            },
            colKey: "modelId",
            title: "逻辑模型",
        },
        {
            cell: ({ row }) => (
                <Tag variant="light">{capabilityLabel(modelTypeOf(modelsById.get(row.modelId ?? "")))}</Tag>
            ),
            colKey: "capability",
            title: "能力",
        },
        {
            cell: ({ row }) => (
                <CopyableIdentifier
                    display={
                        <span className="min-w-0 truncate" title={row.upstreamModelKey}>
                            {row.upstreamModelKey}
                        </span>
                    }
                    value={row.upstreamModelKey}
                />
            ),
            colKey: "upstreamModelKey",
            title: "上游模型",
        },
        {
            cell: ({ row }) => <CopyableIdentifier mask quiet value={row.channelId} />,
            colKey: "channelId",
            title: "渠道",
        },
        {
            cell: ({ row }) => (
                <div className="flex flex-col gap-1 text-xs">
                    <span>
                        优先级 <span className="font-mono tabular-nums">{row.priority ?? "未设置"}</span>
                    </span>
                    <span>
                        权重 <span className="font-mono tabular-nums">{row.weight ?? "未设置"}</span>
                    </span>
                </div>
            ),
            colKey: "priority",
            title: "调度",
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
            cell: ({ row }) => formatDateTime(row.updatedAt),
            colKey: "updatedAt",
            title: "更新时间",
        },
    ];

    return (
        <ConsoleTable
            columns={columns}
            data={filteredRoutes}
            error={error}
            loading={loading}
            onRetry={onRetry}
            rowKey="id"
            tableProps={{ hover: true }}
            title={
                <span className="flex flex-wrap items-center gap-2">
                    渠道路由
                    <Tag size="small" variant="light">
                        {formatCount(filteredRoutes.length)} / {formatCount(routes.length)}
                    </Tag>
                </span>
            }
            toolbar={
                <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                    <div className="flex min-w-0 flex-wrap items-center gap-2">
                        <div className="w-full min-w-0 sm:w-[300px]">
                            <Input
                                clearable
                                onChange={(value) => onKeywordChange(value)}
                                placeholder="搜索模型、上游模型或渠道"
                                prefixIcon={<SearchIcon />}
                                value={keyword}
                            />
                        </div>
                        <div className="w-full min-w-0 sm:w-[210px]">
                            <Select
                                filterable
                                onChange={(value) => onModelChange(String(value))}
                                options={modelOptions}
                                value={modelId}
                            />
                        </div>
                        <div className="w-full min-w-0 sm:w-[150px]">
                            <Select
                                onChange={(value) => onStatusChange(String(value))}
                                options={STATUS_OPTIONS}
                                value={status}
                            />
                        </div>
                    </div>
                    <span className="shrink-0 text-xs text-[var(--td-text-color-secondary)]">
                        按优先级与权重从高到低排列
                    </span>
                </div>
            }
        />
    );
}
