"use client";

import { useMemo, useState } from "react";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ChevronDownIcon, SwapIcon } from "tdesign-icons-react";
import { Alert, Button, Card, Empty, Select, Skeleton, Tag } from "tdesign-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { DescriptionGrid } from "../../../../components/description-grid";
import { EmptyValue } from "../../../../components/empty-value";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatCount, formatDateTime } from "../../../../composables/_format";
import { ProviderMark } from "../../components/provider-mark";
import { type AiChannelModel, type AiDefaultModel, type AiModel, putAiDefaultModel } from "../../composables/_dans";
import { formatTokenLimit, modelCapabilityCodes, modelLabel, modelProvider } from "../../composables/_model-meta";

const SERVICE_SECTIONS = [
    { capability: "chat", description: "创作、推理等看重质量的任务", title: "主模型" },
    { capability: "image", description: "图片生成、编辑与合成任务", title: "图片模型" },
    { capability: "video", description: "视频生成与动态内容任务", title: "视频模型" },
    { capability: "audio", description: "语音合成、音色与音频生成任务", title: "音频模型" },
    {
        capability: "embedding",
        description: "将记忆、文件和知识库内容转为向量，供语义检索使用",
        title: "记忆与知识库向量化",
    },
    { capability: "rerank", description: "对检索结果进行相关度重排", title: "重排模型" },
    { capability: "3d", description: "三维内容生成与建模任务", title: "3D 模型" },
    { capability: "transcription", description: "录音与音频内容的文字转写", title: "语音转写模型" },
    { capability: "memory_extraction", description: "从对话中提取可复用的长期记忆", title: "记忆抽取模型" },
] as const;

type DefaultRoutingProps = {
    defaults: AiDefaultModel[];
    error?: unknown;
    loading: boolean;
    models: AiModel[];
    onRetry: () => void;
    onShowModels: () => void;
    onShowRoutes: (modelId: string) => void;
    routes: AiChannelModel[];
};

export function DefaultRouting({
    defaults,
    error,
    loading,
    models,
    onRetry,
    onShowModels,
    onShowRoutes,
    routes,
}: DefaultRoutingProps) {
    const [expanded, setExpanded] = useState<Set<string>>(() => new Set());
    const queryClient = useQueryClient();
    const prompt = useMessagePrompt();
    const modelsById = useMemo(() => new Map(models.map((model) => [model.id, model])), [models]);
    const defaultsByCapability = useMemo(
        () => new Map(defaults.map((entry) => [entry.capability ?? "", entry])),
        [defaults],
    );
    const modelsByCapability = useMemo(() => {
        const result = new Map<string, AiModel[]>();
        for (const section of SERVICE_SECTIONS) {
            const matched = models
                .filter((model) => modelCapabilityCodes(model).includes(section.capability))
                .sort((left, right) => {
                    const statusOrder = Number(right.status === "active") - Number(left.status === "active");

                    return statusOrder || modelLabel(left).localeCompare(modelLabel(right), "zh-CN");
                });
            if (matched.length > 0) {
                result.set(section.capability, matched);
            }
        }

        return result;
    }, [models]);
    const sections = useMemo(
        () =>
            SERVICE_SECTIONS.filter(
                (section) =>
                    (modelsByCapability.get(section.capability)?.length ?? 0) > 0 ||
                    defaultsByCapability.has(section.capability),
            ),
        [defaultsByCapability, modelsByCapability],
    );
    const saveDefault = useMutation({
        mutationFn: (input: { capability: string; modelId: string }) =>
            putAiDefaultModel(input.capability, input.modelId),
        onError: (mutationError) => prompt.error({ content: consoleErrorMessage(mutationError) }),
        onSuccess: (saved) => {
            queryClient.setQueryData<AiDefaultModel[]>(["dans", "ai", "default-models"], (current = []) => [
                ...current.filter(
                    (entry) => !(entry.workspaceId === saved.workspaceId && entry.capability === saved.capability),
                ),
                saved,
            ]);
            void queryClient.invalidateQueries({ queryKey: ["dans", "ai", "default-models"] });
            prompt.success({ content: "默认模型已更新。" });
        },
    });
    const toggleExpanded = (capability: string) => {
        setExpanded((current) => {
            const next = new Set(current);
            if (next.has(capability)) {
                next.delete(capability);
            } else {
                next.add(capability);
            }

            return next;
        });
    };

    if (loading) {
        return (
            <div className="flex w-full flex-col gap-6">
                {Array.from({ length: 4 }, (_, index) => (
                    <Card bordered className="min-h-40" key={index}>
                        <Skeleton animation="gradient" rowCol={[1, 2, 1]} />
                    </Card>
                ))}
            </div>
        );
    }

    if (error) {
        return (
            <Alert
                message={consoleErrorMessage(error)}
                operation={
                    <Button onClick={onRetry} size="small" variant="text">
                        重试
                    </Button>
                }
                theme="error"
            />
        );
    }

    if (sections.length === 0) {
        return (
            <Card bordered={false}>
                <Empty
                    action={
                        <Button onClick={onShowModels} variant="outline">
                            查看逻辑模型
                        </Button>
                    }
                    description="当前没有可配置的默认模型。请先在 AI 服务商中导入模型。"
                />
            </Card>
        );
    }

    return (
        <div className="flex w-full flex-col gap-8">
            {sections.map((section) => {
                const sectionModels = modelsByCapability.get(section.capability) ?? [];
                const defaultEntry = defaultsByCapability.get(section.capability);
                const selectedModel = defaultEntry?.modelId ? modelsById.get(defaultEntry.modelId) : undefined;
                const modelRoutes = selectedModel
                    ? routes
                          .filter((route) => route.modelId === selectedModel.id)
                          .sort(
                              (left, right) =>
                                  (left.priority ?? Number.MAX_SAFE_INTEGER) -
                                      (right.priority ?? Number.MAX_SAFE_INTEGER) ||
                                  (right.weight ?? 0) - (left.weight ?? 0),
                          )
                    : [];
                const isExpanded = expanded.has(section.capability);
                const saving = saveDefault.isPending && saveDefault.variables?.capability === section.capability;

                return (
                    <section className="flex min-w-0 flex-col gap-3" key={section.capability}>
                        <div className="min-w-0">
                            <h3 className="m-0 text-lg leading-7 font-semibold">{section.title}</h3>
                            <p className="m-0 mt-1 text-sm text-[var(--td-text-color-secondary)]">
                                {section.description}
                            </p>
                        </div>
                        <div className="overflow-hidden rounded-xl border border-[var(--td-border-level-1-color)] bg-[var(--td-bg-color-container)]">
                            <div className="flex flex-col gap-3 px-6 py-5 md:flex-row md:items-center md:justify-between">
                                <span className="shrink-0 text-base font-medium">模型</span>
                                <div className="w-full md:w-[380px]">
                                    <Select
                                        filterable
                                        loading={saving}
                                        onChange={(value) => {
                                            if (value === undefined || value === null || value === "") {
                                                return;
                                            }
                                            saveDefault.mutate({
                                                capability: section.capability,
                                                modelId: String(value),
                                            });
                                        }}
                                        options={sectionModels.map((model) => ({
                                            disabled: model.status !== "active",
                                            label: (
                                                <span className="flex min-w-0 items-center gap-2">
                                                    <ProviderMark provider={modelProvider(model)} size="small" />
                                                    <span className="truncate">{modelLabel(model)}</span>
                                                    {model.status === "active" ? null : (
                                                        <Tag size="small" variant="light">
                                                            停用
                                                        </Tag>
                                                    )}
                                                </span>
                                            ),
                                            value: model.id,
                                        }))}
                                        placeholder="选择默认模型"
                                        value={defaultEntry?.modelId}
                                    />
                                </div>
                            </div>
                            <button
                                className="flex w-full items-center justify-between gap-4 border-t border-[var(--td-border-level-1-color)] px-6 py-3.5 text-left transition-colors hover:bg-[var(--td-bg-color-container-hover)]"
                                onClick={() => toggleExpanded(section.capability)}
                                type="button"
                            >
                                <span className="min-w-0 truncate text-sm text-[var(--td-text-color-secondary)]">
                                    高级{" "}
                                    {defaultEntry
                                        ? `${formatCount(modelRoutes.length)} 条渠道路由，更新于 ${formatDateTime(defaultEntry.updatedAt)}`
                                        : `${formatCount(sectionModels.length)} 个可用模型`}
                                </span>
                                <span className="inline-flex shrink-0 items-center gap-1 text-sm">
                                    {isExpanded ? "收起" : "展开"}
                                    <ChevronDownIcon
                                        className={`transition-transform ${isExpanded ? "rotate-180" : ""}`}
                                        size={16}
                                    />
                                </span>
                            </button>
                            {isExpanded ? (
                                <div className="border-t border-[var(--td-border-level-1-color)] px-6 py-5">
                                    {selectedModel ? (
                                        <div className="flex min-w-0 flex-col gap-5">
                                            <DescriptionGrid
                                                columns={2}
                                                items={[
                                                    {
                                                        label: "逻辑名称",
                                                        value: (
                                                            <span className="font-mono text-xs">
                                                                {selectedModel.logicalName ?? <EmptyValue />}
                                                            </span>
                                                        ),
                                                    },
                                                    { label: "服务商", value: modelProvider(selectedModel) },
                                                    {
                                                        label: "上下文",
                                                        value: formatTokenLimit(selectedModel.contextWindow),
                                                    },
                                                    {
                                                        label: "最大输出",
                                                        value: formatTokenLimit(selectedModel.maxOutputTokens),
                                                    },
                                                ]}
                                            />
                                            <div className="min-w-0">
                                                <div className="mb-2 flex items-center justify-between gap-3">
                                                    <span className="text-sm font-medium">渠道路由</span>
                                                    <Button
                                                        icon={<SwapIcon />}
                                                        onClick={() => onShowRoutes(selectedModel.id)}
                                                        size="small"
                                                        variant="text"
                                                    >
                                                        查看路由
                                                    </Button>
                                                </div>
                                                {modelRoutes.length === 0 ? (
                                                    <p className="m-0 text-sm text-[var(--td-text-color-secondary)]">
                                                        该模型当前没有可用渠道路由。
                                                    </p>
                                                ) : (
                                                    <div className="divide-y divide-[var(--td-border-level-1-color)] rounded-lg border border-[var(--td-border-level-1-color)]">
                                                        {modelRoutes.map((route) => (
                                                            <div
                                                                className="flex flex-wrap items-center justify-between gap-2 px-3 py-2.5"
                                                                key={route.id}
                                                            >
                                                                <span
                                                                    className="min-w-0 truncate font-mono text-xs"
                                                                    title={route.upstreamModelKey ?? route.id}
                                                                >
                                                                    {route.upstreamModelKey ?? route.id}
                                                                </span>
                                                                <span className="flex shrink-0 items-center gap-2 text-xs text-[var(--td-text-color-secondary)]">
                                                                    优先级 {route.priority ?? "—"} · 权重{" "}
                                                                    {route.weight ?? "—"}
                                                                    <Tag
                                                                        size="small"
                                                                        theme={route.enabled ? "success" : "default"}
                                                                        variant="light"
                                                                    >
                                                                        {route.enabled ? "启用" : "停用"}
                                                                    </Tag>
                                                                </span>
                                                            </div>
                                                        ))}
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    ) : (
                                        <p className="m-0 text-sm text-[var(--td-text-color-secondary)]">
                                            尚未配置默认模型。选择上方模型后会立即保存，并优先使用当前工作区的可用渠道路由。
                                        </p>
                                    )}
                                </div>
                            ) : null}
                        </div>
                    </section>
                );
            })}
        </div>
    );
}
