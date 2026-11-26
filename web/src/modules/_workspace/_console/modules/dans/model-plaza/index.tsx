"use client";

import { useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { ChartBubbleIcon, RefreshIcon, SearchIcon } from "tdesign-icons-react";
import { Button, Empty, Input, Tag } from "tdesign-react";

import { formatCountOrDash } from "../../../composables/_format";
import { DansPage } from "../components/page";
import { type AiModel, listAiChannelModels, listAiDefaultModels, listAiModels } from "../composables/_dans";

const CAPABILITY_LABELS: Record<string, string> = {
    chat: "对话",
    image: "图片",
    video: "视频",
    audio: "音频",
    embedding: "向量化",
    rerank: "重排",
    "3d": "3D",
};

export function ModelPlazaView() {
    const [keyword, setKeyword] = useState("");
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
    const trimmedKeyword = keyword.trim().toLowerCase();
    const filteredModels = trimmedKeyword
        ? models.filter((model) =>
              [model.displayName, model.logicalName, model.description].some((value) =>
                  value?.toLowerCase().includes(trimmedKeyword),
              ),
          )
        : models;
    const refresh = () => {
        void modelsQuery.refetch();
        void routesQuery.refetch();
        void defaultsQuery.refetch();
    };
    const routeCount = (modelId: string) => routes.filter((route) => route.modelId === modelId).length;
    const defaultCount = (modelId: string) => defaults.filter((model) => model.modelId === modelId).length;

    return (
        <DansPage
            actions={
                <Button
                    icon={<RefreshIcon />}
                    loading={modelsQuery.isFetching || routesQuery.isFetching || defaultsQuery.isFetching}
                    onClick={refresh}
                    variant="outline"
                >
                    刷新
                </Button>
            }
            description="模型广场汇总平台逻辑模型的能力、上下文、渠道覆盖与默认路由配置。"
            icon={<ChartBubbleIcon size={22} />}
            metrics={[
                { label: "模型总数", value: formatCountOrDash(models.length) },
                {
                    label: "启用模型",
                    value: formatCountOrDash(models.filter((model) => model.status === "active").length),
                },
                { label: "渠道路由", value: formatCountOrDash(routes.length) },
                { label: "默认路由", value: formatCountOrDash(defaults.length) },
            ]}
            section="AI"
            title="模型广场"
        >
            <Input
                className="w-full sm:w-[320px]"
                clearable
                onChange={(value) => setKeyword(value)}
                placeholder="搜索模型名称、逻辑名或说明"
                prefixIcon={<SearchIcon />}
                value={keyword}
            />
            {filteredModels.length === 0 ? (
                <Empty description={modelsQuery.isPending ? "正在加载模型..." : "没有匹配的模型。"} />
            ) : (
                <div className="grid min-w-0 grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
                    {filteredModels.map((model) => (
                        <ModelCard
                            defaultCount={defaultCount(model.id)}
                            key={model.id}
                            model={model}
                            routeCount={routeCount(model.id)}
                        />
                    ))}
                </div>
            )}
        </DansPage>
    );
}

function ModelCard({ defaultCount, model, routeCount }: { defaultCount: number; model: AiModel; routeCount: number }) {
    const capabilities = Object.keys(model.capabilities ?? {});

    return (
        <div className="flex min-w-0 flex-col gap-3 rounded-xl border border-[var(--td-border-level-1-color)] bg-[var(--td-bg-color-container)] p-4">
            <div className="flex min-w-0 items-start justify-between gap-3">
                <div className="min-w-0">
                    <div className="truncate font-semibold">{model.displayName ?? model.logicalName ?? model.id}</div>
                    <div className="truncate font-mono text-xs text-[var(--td-text-color-secondary)]">
                        {model.logicalName ?? model.id}
                    </div>
                </div>
                <Tag theme={model.status === "active" ? "success" : "default"} variant="light">
                    {model.status === "active" ? "启用" : "停用"}
                </Tag>
            </div>
            <p className="m-0 line-clamp-2 min-h-10 text-sm text-[var(--td-text-color-secondary)]">
                {model.description ?? "暂无模型说明。"}
            </p>
            <div className="flex flex-wrap gap-1">
                {capabilities.length > 0 ? (
                    capabilities.map((capability) => (
                        <Tag key={capability} variant="light">
                            {CAPABILITY_LABELS[capability] ?? capability}
                        </Tag>
                    ))
                ) : (
                    <span className="text-xs text-[var(--td-text-color-placeholder)]">暂无能力标签</span>
                )}
            </div>
            <div className="grid grid-cols-2 gap-2 border-t border-[var(--td-border-level-1-color)] pt-3 text-xs">
                <Metric label="上下文" value={formatCountOrDash(model.contextWindow)} />
                <Metric label="最大输出" value={formatCountOrDash(model.maxOutputTokens)} />
                <Metric label="渠道路由" value={formatCountOrDash(routeCount)} />
                <Metric label="默认路由" value={formatCountOrDash(defaultCount)} />
            </div>
        </div>
    );
}

function Metric({ label, value }: { label: string; value: string }) {
    return (
        <div className="flex flex-col gap-0.5">
            <span className="text-[var(--td-text-color-secondary)]">{label}</span>
            <span className="font-medium tabular-nums">{value}</span>
        </div>
    );
}
