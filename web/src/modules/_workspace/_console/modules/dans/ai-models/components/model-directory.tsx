"use client";

import { useMemo, useState } from "react";

import { SearchIcon, SwapIcon } from "tdesign-icons-react";
import { Alert, Button, Card, Empty, Input, Select, Skeleton, Tag, Tooltip } from "tdesign-react";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatCount, formatDateTime } from "../../../../composables/_format";
import { ProviderMark } from "../../components/provider-mark";
import type { AiModel } from "../../composables/_dans";
import {
    MODEL_FEATURE_LABELS,
    MODEL_TYPE_OPTIONS,
    capabilityLabel,
    formatTokenLimit,
    modalityLabel,
    modelCapabilityCodes,
    modelDescription,
    modelFeatures,
    modelLabel,
    modelModalities,
    modelProvider,
} from "../../composables/_model-meta";

const STATUS_OPTIONS = [
    { label: "全部状态", value: "all" },
    { label: "已启用", value: "active" },
    { label: "已停用", value: "inactive" },
];

const CAPABILITY_OPTIONS = [
    { label: "全部能力", value: "all" },
    ...MODEL_TYPE_OPTIONS.map((option) => ({ label: option.label, value: option.value })),
];

type ModelDirectoryProps = {
    defaultCounts: Map<string, number>;
    error?: unknown;
    loading: boolean;
    models: AiModel[];
    onRetry: () => void;
    onShowRoutes: (modelId: string) => void;
    routeCounts: Map<string, number>;
};

export function ModelDirectory({
    defaultCounts,
    error,
    loading,
    models,
    onRetry,
    onShowRoutes,
    routeCounts,
}: ModelDirectoryProps) {
    const [keyword, setKeyword] = useState("");
    const [capability, setCapability] = useState("all");
    const [status, setStatus] = useState("all");
    const normalizedKeyword = keyword.trim().toLowerCase();
    const filteredModels = useMemo(
        () =>
            models
                .filter((model) => {
                    const isActive = model.status === "active";
                    if (status === "active" && !isActive) {
                        return false;
                    }
                    if (status === "inactive" && isActive) {
                        return false;
                    }
                    if (capability !== "all" && !modelCapabilityCodes(model).includes(capability)) {
                        return false;
                    }
                    if (!normalizedKeyword) {
                        return true;
                    }

                    return [
                        modelLabel(model),
                        model.logicalName,
                        model.id,
                        modelDescription(model),
                        ...modelCapabilityCodes(model).map(capabilityLabel),
                        ...modelFeatures(model).map((feature) => MODEL_FEATURE_LABELS[feature] ?? feature),
                    ].some((value) => value?.toLowerCase().includes(normalizedKeyword));
                })
                .sort((left, right) => {
                    const statusOrder = Number(right.status === "active") - Number(left.status === "active");

                    return statusOrder || modelLabel(left).localeCompare(modelLabel(right), "zh-CN");
                }),
        [capability, models, normalizedKeyword, status],
    );
    const hasFilters = Boolean(keyword) || capability !== "all" || status !== "all";
    const clearFilters = () => {
        setKeyword("");
        setCapability("all");
        setStatus("all");
    };

    return (
        <div className="flex min-w-0 flex-col gap-4">
            {error ? (
                <Alert
                    message={consoleErrorMessage(error)}
                    operation={
                        <Button onClick={onRetry} size="small" variant="text">
                            重试
                        </Button>
                    }
                    theme="error"
                />
            ) : null}
            <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                <div className="flex min-w-0 flex-wrap items-center gap-2">
                    <div className="w-full min-w-0 sm:w-[300px]">
                        <Input
                            clearable
                            onChange={(value) => setKeyword(value)}
                            placeholder="搜索模型名称、逻辑名或说明"
                            prefixIcon={<SearchIcon />}
                            value={keyword}
                        />
                    </div>
                    <div className="w-full min-w-0 sm:w-[160px]">
                        <Select
                            onChange={(value) => setCapability(String(value))}
                            options={CAPABILITY_OPTIONS}
                            value={capability}
                        />
                    </div>
                    <div className="w-full min-w-0 sm:w-[150px]">
                        <Select
                            onChange={(value) => setStatus(String(value))}
                            options={STATUS_OPTIONS}
                            value={status}
                        />
                    </div>
                    {hasFilters ? (
                        <Button onClick={clearFilters} size="small" variant="text">
                            清除筛选
                        </Button>
                    ) : null}
                </div>
                <span className="shrink-0 text-xs text-[var(--td-text-color-secondary)]">
                    {filteredModels.length === models.length
                        ? `共 ${formatCount(models.length)} 个模型`
                        : `筛选出 ${formatCount(filteredModels.length)} / ${formatCount(models.length)} 个模型`}
                </span>
            </div>

            {loading ? (
                <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-2 2xl:grid-cols-3">
                    {Array.from({ length: 6 }, (_, index) => (
                        <Card bordered className="min-w-0" key={index}>
                            <Skeleton animation="gradient" rowCol={[1, 2, 1, 2]} />
                        </Card>
                    ))}
                </div>
            ) : filteredModels.length === 0 ? (
                <Card bordered={false}>
                    <Empty
                        action={
                            hasFilters ? (
                                <Button onClick={clearFilters} variant="outline">
                                    清除筛选
                                </Button>
                            ) : undefined
                        }
                        description={hasFilters ? "没有匹配的模型。" : "当前没有可查看的逻辑模型。"}
                    />
                </Card>
            ) : (
                <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-2 2xl:grid-cols-3">
                    {filteredModels.map((model) => {
                        const routeCount = routeCounts.get(model.id) ?? 0;

                        return (
                            <ModelCard
                                defaultCount={defaultCounts.get(model.id) ?? 0}
                                key={model.id}
                                model={model}
                                onShowRoutes={() => onShowRoutes(model.id)}
                                routeCount={routeCount}
                            />
                        );
                    })}
                </div>
            )}
        </div>
    );
}

function ModelCard({
    defaultCount,
    model,
    onShowRoutes,
    routeCount,
}: {
    defaultCount: number;
    model: AiModel;
    onShowRoutes: () => void;
    routeCount: number;
}) {
    const capabilityCodes = modelCapabilityCodes(model);
    const features = modelFeatures(model);
    const modalities = modelModalities(model);
    const description = modelDescription(model);
    const active = model.status === "active";

    return (
        <Card bordered className="h-full min-w-0 transition-colors hover:border-[var(--td-brand-color)]">
            <article className="flex min-w-0 flex-col gap-4">
                <div className="flex min-w-0 items-start gap-3">
                    <ProviderMark provider={modelProvider(model)} />
                    <div className="min-w-0 flex-1">
                        <div className="flex min-w-0 items-start justify-between gap-2">
                            <h3 className="m-0 truncate text-base font-semibold" title={modelLabel(model)}>
                                {modelLabel(model)}
                            </h3>
                            <Tag theme={active ? "success" : "default"} variant="light">
                                {active ? "启用" : "停用"}
                            </Tag>
                        </div>
                        <div className="mt-1 min-w-0">
                            <CopyableIdentifier
                                className="max-w-full"
                                display={
                                    <span className="min-w-0 truncate" title={model.logicalName ?? model.id}>
                                        {model.logicalName ?? model.id}
                                    </span>
                                }
                                quiet
                                value={model.logicalName ?? model.id}
                            />
                        </div>
                    </div>
                </div>

                <p className="m-0 line-clamp-2 min-h-10 text-sm text-[var(--td-text-color-secondary)]">
                    {description ?? "暂无模型说明。"}
                </p>

                <div className="flex flex-wrap content-start gap-1.5">
                    {capabilityCodes.length > 0 ? (
                        capabilityCodes.map((code) => (
                            <Tag key={code} theme="primary" variant="light">
                                {capabilityLabel(code)}
                            </Tag>
                        ))
                    ) : (
                        <span className="text-xs text-[var(--td-text-color-placeholder)]">未声明能力</span>
                    )}
                    {features.map((feature) => (
                        <Tooltip content="模型能力" key={feature}>
                            <Tag variant="outline">{MODEL_FEATURE_LABELS[feature] ?? feature}</Tag>
                        </Tooltip>
                    ))}
                </div>

                <div className="text-xs text-[var(--td-text-color-placeholder)]">
                    {modalities.input.length > 0 || modalities.output.length > 0 ? (
                        <>
                            输入 {modalities.input.map(modalityLabel).join(" / ") || "未声明"}
                            <span className="mx-1.5">·</span>
                            输出 {modalities.output.map(modalityLabel).join(" / ") || "未声明"}
                        </>
                    ) : (
                        "未声明输入与输出模态"
                    )}
                </div>

                <div className="grid grid-cols-2 gap-3 border-y border-[var(--td-border-level-1-color)] py-3 sm:grid-cols-4">
                    <ModelMetric label="渠道路由" value={formatCount(routeCount)} />
                    <ModelMetric label="默认路由" value={formatCount(defaultCount)} />
                    <ModelMetric label="上下文" value={formatTokenLimit(model.contextWindow)} />
                    <ModelMetric label="最大输出" value={formatTokenLimit(model.maxOutputTokens)} />
                </div>

                <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="text-xs text-[var(--td-text-color-placeholder)]">
                        更新于 {formatDateTime(model.updatedAt)}
                    </span>
                    {routeCount > 0 ? (
                        <Button icon={<SwapIcon />} onClick={onShowRoutes} size="small" variant="text">
                            查看 {formatCount(routeCount)} 条路由
                        </Button>
                    ) : (
                        <span className="text-xs text-[var(--td-text-color-placeholder)]">暂无渠道路由</span>
                    )}
                </div>
            </article>
        </Card>
    );
}

function ModelMetric({ label, value }: { label: string; value: string }) {
    return (
        <div className="flex min-w-0 flex-col gap-0.5">
            <span className="text-[11px] text-[var(--td-text-color-secondary)]">{label}</span>
            <span className="truncate font-medium tabular-nums" title={value}>
                {value}
            </span>
        </div>
    );
}
