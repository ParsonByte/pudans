"use client";

import { useMemo, useState } from "react";

import {
    AddIcon,
    AudioIcon,
    BrowseIcon,
    ChartBubbleIcon,
    ChatIcon,
    DataBaseIcon,
    EditIcon,
    ImageIcon,
    MoreIcon,
    RefreshIcon,
    SearchIcon,
    ToolsIcon,
    VideoIcon,
} from "tdesign-icons-react";
import { Button, Dropdown, Empty, Input, Skeleton, Switch, Tabs, Tag, Tooltip } from "tdesign-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";

import type { AiChannel, AiChannelModel, AiModel, AiProviderDescriptor } from "../../composables/_dans";
import {
    MODEL_FEATURE_LABELS,
    MODEL_TABS,
    MODEL_TYPE_LABELS,
    type ModelTab,
    formatTokenLimit,
    modelFeatures,
    modelModalities,
    modelTypeOf,
} from "../composables/model-types";
import { ProviderMark } from "./provider-mark";

type ChannelModelsProps = {
    channel?: AiChannel;
    descriptor: AiProviderDescriptor;
    fetching: boolean;
    loading: boolean;
    models: AiModel[];
    onAddCustom: () => void;
    onEdit: (route: AiChannelModel, model?: AiModel) => void;
    onFetch: () => Promise<void>;
    onRefresh: () => Promise<void>;
    onToggle: (route: AiChannelModel, enabled: boolean) => Promise<void>;
    routes: AiChannelModel[];
};

const TAB_ICONS = {
    all: DataBaseIcon,
    chat: ChatIcon,
    image: ImageIcon,
    video: VideoIcon,
    audio: AudioIcon,
    embedding: ChartBubbleIcon,
} as const;

const FEATURE_ICONS = {
    tools: ToolsIcon,
    vision: BrowseIcon,
    reasoning: ChartBubbleIcon,
    "web-search": SearchIcon,
    image: ImageIcon,
    video: VideoIcon,
    audio: AudioIcon,
    "image-generation": ImageIcon,
    "video-generation": VideoIcon,
    "audio-generation": AudioIcon,
    asr: AudioIcon,
} as const;

type ModelRowData = {
    model?: AiModel;
    route: AiChannelModel;
};

function modelName(row: ModelRowData): string {
    return row.model?.displayName ?? row.model?.logicalName ?? row.route.upstreamModelKey ?? "未命名模型";
}

function matchesSearch(row: ModelRowData, search: string): boolean {
    if (!search) {
        return true;
    }
    const normalized = search.toLowerCase();

    return [modelName(row), row.model?.logicalName, row.route.upstreamModelKey].some((value) =>
        value?.toLowerCase().includes(normalized),
    );
}

function matchesTab(row: ModelRowData, tab: ModelTab): boolean {
    return tab === "all" || modelTypeOf(row.model) === tab;
}

function FeatureBadge({ code }: { code: string }) {
    const Icon = FEATURE_ICONS[code as keyof typeof FEATURE_ICONS];
    const label = MODEL_FEATURE_LABELS[code] ?? code;
    if (!Icon) {
        return <Tag variant="light">{label}</Tag>;
    }

    return (
        <Tooltip content={label}>
            <span
                aria-label={label}
                className="grid size-6 place-items-center rounded-md bg-[var(--td-brand-color-light)] text-[var(--td-brand-color)]"
            >
                <Icon size="14px" />
            </span>
        </Tooltip>
    );
}

function ModelRow({
    logoUrl,
    onEdit,
    onToggle,
    provider,
    row,
}: {
    logoUrl?: string;
    onEdit: (route: AiChannelModel, model?: AiModel) => void;
    onToggle: (route: AiChannelModel, enabled: boolean) => Promise<void>;
    provider: string;
    row: ModelRowData;
}) {
    const prompt = useMessagePrompt();
    const [pending, setPending] = useState(false);
    const features = modelFeatures(row.model);
    const modalities = modelModalities(row.model);
    const type = modelTypeOf(row.model);
    const upstream = row.route.upstreamModelKey ?? "—";
    const upstreamVisible = upstream !== "—";

    const toggle = async (value: boolean | string) => {
        if (typeof value !== "boolean") {
            return;
        }
        setPending(true);
        try {
            await onToggle(row.route, value);
        } catch (error) {
            prompt.error({ content: consoleErrorMessage(error, "模型状态更新失败。") });
        } finally {
            setPending(false);
        }
    };

    return (
        <article className="flex flex-wrap items-center gap-3 border-b border-[var(--td-border-level-1-color)] px-4 py-3 last:border-b-0">
            <ProviderMark logoUrl={logoUrl} provider={provider} size="small" />
            <div className="min-w-0 flex-1">
                <div className="flex min-w-0 flex-wrap items-center gap-2">
                    <span className="truncate text-sm font-medium">{modelName(row)}</span>
                    {upstreamVisible ? (
                        <Tooltip content="上游模型标识">
                            <Tag size="small" variant="light">
                                <span className="font-mono">{upstream}</span>
                            </Tag>
                        </Tooltip>
                    ) : null}
                    <Tooltip content="修改模型">
                        <Button
                            aria-label={`修改 ${modelName(row)}`}
                            icon={<EditIcon />}
                            onClick={() => onEdit(row.route, row.model)}
                            shape="square"
                            size="small"
                            variant="text"
                        />
                    </Tooltip>
                </div>
                {row.model?.description ? (
                    <p className="m-0 mt-1 line-clamp-1 text-xs text-[var(--td-text-color-secondary)]">
                        {row.model.description}
                    </p>
                ) : null}
                <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-[var(--td-text-color-placeholder)]">
                    {features.map((feature) => (
                        <span key={feature}>{MODEL_FEATURE_LABELS[feature] ?? feature}</span>
                    ))}
                    {modalities.input.length > 0 ? <span>输入 {modalities.input.join("/")}</span> : null}
                    {modalities.output.length > 0 ? <span>输出 {modalities.output.join("/")}</span> : null}
                    {type !== "chat" && features.length === 0 ? <span>{MODEL_TYPE_LABELS[type] ?? type}</span> : null}
                </div>
            </div>
            <div className="flex shrink-0 items-center gap-2">
                {features.map((feature) => (
                    <FeatureBadge code={feature} key={feature} />
                ))}
                <Tag variant="light">{formatTokenLimit(row.model?.contextWindow)}</Tag>
                <Switch
                    aria-label={`${modelName(row)} 启用状态`}
                    loading={pending}
                    onChange={(value) => void toggle(value)}
                    size="small"
                    value={row.route.enabled}
                />
            </div>
        </article>
    );
}

export function ChannelModels({
    channel,
    descriptor,
    fetching,
    loading,
    models,
    onAddCustom,
    onEdit,
    onFetch,
    onRefresh,
    onToggle,
    routes,
}: ChannelModelsProps) {
    const prompt = useMessagePrompt();
    const [tab, setTab] = useState<ModelTab>("all");
    const [search, setSearch] = useState("");
    const [refreshing, setRefreshing] = useState(false);
    const modelById = useMemo(() => new Map(models.map((model) => [model.id, model])), [models]);
    const rows = useMemo<ModelRowData[]>(
        () =>
            routes.map((route) => ({
                model: route.modelId ? modelById.get(route.modelId) : undefined,
                route,
            })),
        [modelById, routes],
    );
    const counts = useMemo(
        () =>
            Object.fromEntries(
                MODEL_TABS.map((item) => [item.value, rows.filter((row) => matchesTab(row, item.value)).length]),
            ) as Record<ModelTab, number>,
        [rows],
    );
    const visibleTabs = useMemo(() => {
        const declared = new Set((descriptor.modelTypes ?? []).filter(Boolean));
        return MODEL_TABS.filter((item) => item.value === "all" || counts[item.value] > 0 || declared.has(item.value));
    }, [counts, descriptor.modelTypes]);
    const activeTab = visibleTabs.some((item) => item.value === tab) ? tab : "all";
    const visibleRows = rows.filter((row) => matchesTab(row, activeTab) && matchesSearch(row, search.trim()));
    const enabledRows = visibleRows.filter((row) => row.route.enabled);
    const disabledRows = visibleRows.filter((row) => !row.route.enabled);
    const unavailable = !channel;

    const fetchModels = async () => {
        try {
            await onFetch();
        } catch (error) {
            prompt.error({ content: consoleErrorMessage(error, "获取模型列表失败。") });
        }
    };

    const refresh = async () => {
        setRefreshing(true);
        try {
            await onRefresh();
        } catch (error) {
            prompt.error({ content: consoleErrorMessage(error, "刷新模型列表失败。") });
        } finally {
            setRefreshing(false);
        }
    };

    return (
        <section className="flex min-w-0 flex-col border-t border-[var(--td-border-level-1-color)] p-4 lg:p-6">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
                <h3 className="m-0 text-base font-semibold">模型列表</h3>
                <div className="flex w-full min-w-0 flex-wrap items-center gap-2 sm:ml-auto sm:w-auto sm:flex-nowrap">
                    <div className="w-full min-w-0 sm:w-[220px]">
                        <Input
                            clearable
                            onChange={setSearch}
                            placeholder="搜索模型..."
                            prefixIcon={<SearchIcon />}
                            size="small"
                            value={search}
                        />
                    </div>
                    <Button
                        disabled={unavailable}
                        icon={<RefreshIcon />}
                        loading={fetching}
                        onClick={() => void fetchModels()}
                        size="small"
                        variant="outline"
                    >
                        获取模型列表
                    </Button>
                    <Tooltip content={unavailable ? "请先保存渠道配置" : "创建自定义模型"}>
                        <Button
                            aria-label="创建自定义模型"
                            disabled={unavailable}
                            icon={<AddIcon />}
                            onClick={onAddCustom}
                            shape="square"
                            size="small"
                            variant="outline"
                        />
                    </Tooltip>
                    <Dropdown
                        onClick={(item) => {
                            if (item.value === "refresh") {
                                void refresh();
                            } else if (descriptor.documentationUrl) {
                                window.open(descriptor.documentationUrl, "_blank", "noopener,noreferrer");
                            }
                        }}
                        options={[
                            { content: "刷新列表", value: "refresh" },
                            ...(descriptor.documentationUrl ? [{ content: "打开服务商文档", value: "docs" }] : []),
                        ]}
                        trigger="click"
                    >
                        <Button
                            aria-label="模型列表操作"
                            icon={<MoreIcon />}
                            shape="square"
                            size="small"
                            variant="text"
                        />
                    </Dropdown>
                </div>
            </div>

            <Tabs
                action={refreshing ? <Skeleton animation="gradient" theme="text" /> : undefined}
                className="mt-2"
                list={visibleTabs.map((item) => {
                    const Icon = TAB_ICONS[item.value];

                    return {
                        label: (
                            <span className="inline-flex items-center gap-1.5">
                                <Icon size="16px" />
                                <span>
                                    {item.label} ({counts[item.value]})
                                </span>
                            </span>
                        ),
                        value: item.value,
                    };
                })}
                onChange={(value) => setTab(String(value) as ModelTab)}
                value={activeTab}
            />

            <div className="mt-4 min-h-48">
                {loading ? (
                    <div className="flex flex-col gap-3 p-2">
                        <Skeleton animation="gradient" theme="text" />
                        <Skeleton animation="gradient" theme="text" />
                        <Skeleton animation="gradient" theme="text" />
                    </div>
                ) : visibleRows.length === 0 ? (
                    <Empty
                        description={
                            unavailable
                                ? "保存渠道配置后可获取真实模型列表"
                                : search
                                  ? "没有匹配的模型"
                                  : "暂无模型，请获取模型列表"
                        }
                    />
                ) : (
                    <div className="flex flex-col gap-4">
                        <ModelGroup
                            label="已启用"
                            logoUrl={descriptor.logoUrl}
                            onEdit={onEdit}
                            onToggle={onToggle}
                            provider={descriptor.provider}
                            rows={enabledRows}
                        />
                        <ModelGroup
                            label="未启用"
                            logoUrl={descriptor.logoUrl}
                            onEdit={onEdit}
                            onToggle={onToggle}
                            provider={descriptor.provider}
                            rows={disabledRows}
                        />
                    </div>
                )}
            </div>
        </section>
    );
}

function ModelGroup({
    label,
    logoUrl,
    onEdit,
    onToggle,
    provider,
    rows,
}: {
    label: string;
    logoUrl?: string;
    onEdit: (route: AiChannelModel, model?: AiModel) => void;
    onToggle: (route: AiChannelModel, enabled: boolean) => Promise<void>;
    provider: string;
    rows: ModelRowData[];
}) {
    if (rows.length === 0) {
        return null;
    }

    return (
        <section>
            <div className="mb-1 flex items-center gap-2 px-1 text-xs text-[var(--td-text-color-placeholder)]">
                <span>{label}</span>
                <span>{rows.length}</span>
            </div>
            <div className="overflow-hidden rounded-lg border border-[var(--td-border-level-1-color)]">
                {rows.map((row) => (
                    <ModelRow
                        key={row.route.id}
                        logoUrl={logoUrl}
                        onEdit={onEdit}
                        onToggle={onToggle}
                        provider={provider}
                        row={row}
                    />
                ))}
            </div>
        </section>
    );
}
