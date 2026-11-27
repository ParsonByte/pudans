"use client";

import { useMemo, useState } from "react";

import { SearchIcon, ToolsIcon } from "tdesign-icons-react";
import { Alert, Button, Card, Empty, Input, Select, Skeleton, Tag } from "tdesign-react";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatCount } from "../../../../composables/_format";
import type { AiToolDescriptor } from "../../composables/_dans";
import { CATALOG_STATUS_OPTIONS } from "../composables/_catalog";

type CatalogToolsProps = {
    error?: unknown;
    loading: boolean;
    onRetry: () => void;
    tools: AiToolDescriptor[];
};

export function CatalogTools({ error, loading, onRetry, tools }: CatalogToolsProps) {
    const [keyword, setKeyword] = useState("");
    const [status, setStatus] = useState("all");
    const normalizedKeyword = keyword.trim().toLowerCase();
    const hasFilters = Boolean(keyword) || status !== "all";
    const filteredTools = useMemo(
        () =>
            tools.filter((tool) => {
                if (status === "enabled" && tool.enabled === false) {
                    return false;
                }
                if (status === "disabled" && tool.enabled !== false) {
                    return false;
                }
                if (!normalizedKeyword) {
                    return true;
                }

                return [tool.key, tool.label, tool.description].some((value) =>
                    value?.toLowerCase().includes(normalizedKeyword),
                );
            }),
        [normalizedKeyword, status, tools],
    );
    const resetFilters = () => {
        setKeyword("");
        setStatus("all");
    };

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

    if (loading) {
        return (
            <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-2 2xl:grid-cols-3">
                {Array.from({ length: 6 }, (_, index) => (
                    <Card bordered className="min-w-0" key={index}>
                        <Skeleton animation="gradient" rowCol={[1, 2, 1]} />
                    </Card>
                ))}
            </div>
        );
    }

    if (tools.length === 0) {
        return (
            <Card bordered={false}>
                <Empty description="运行时还没有注册可用的 AI 工具。" />
            </Card>
        );
    }

    if (filteredTools.length === 0) {
        return (
            <Card bordered={false}>
                <Empty
                    action={
                        <Button onClick={resetFilters} variant="outline">
                            清除筛选
                        </Button>
                    }
                    description="没有匹配的 AI 工具。"
                />
            </Card>
        );
    }

    return (
        <div className="flex min-w-0 flex-col gap-4">
            <div className="flex min-w-0 flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                <div className="flex min-w-0 flex-wrap items-center gap-2">
                    <div className="w-full min-w-0 sm:w-[320px]">
                        <Input
                            clearable
                            onChange={(value) => setKeyword(value)}
                            placeholder="搜索工具名称、键或说明"
                            prefixIcon={<SearchIcon />}
                            value={keyword}
                        />
                    </div>
                    <div className="w-full min-w-0 sm:w-[140px]">
                        <Select
                            onChange={(value) => setStatus(String(value))}
                            options={CATALOG_STATUS_OPTIONS.map((option) => ({ ...option }))}
                            value={status}
                        />
                    </div>
                    {hasFilters ? (
                        <Button onClick={resetFilters} size="small" variant="text">
                            清除筛选
                        </Button>
                    ) : null}
                </div>
                <span className="shrink-0 text-xs text-[var(--td-text-color-secondary)]">
                    {filteredTools.length === tools.length
                        ? `共 ${formatCount(tools.length)} 个 AI 工具`
                        : `筛选出 ${formatCount(filteredTools.length)} / ${formatCount(tools.length)} 个 AI 工具`}
                </span>
            </div>

            <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-2 2xl:grid-cols-3">
                {filteredTools.map((tool) => (
                    <Card bordered className="h-full min-w-0" key={tool.key}>
                        <article className="flex h-full min-w-0 flex-col gap-3">
                            <div className="flex min-w-0 items-start gap-3">
                                <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-[var(--td-brand-color-light)] text-[var(--td-brand-color)]">
                                    <ToolsIcon size={20} />
                                </span>
                                <div className="min-w-0 flex-1">
                                    <div className="flex min-w-0 items-start justify-between gap-2">
                                        <h3
                                            className="m-0 truncate text-base font-semibold"
                                            title={tool.label ?? tool.key}
                                        >
                                            {tool.label ?? tool.key}
                                        </h3>
                                        <Tag theme={tool.enabled === false ? "default" : "success"} variant="light">
                                            {tool.enabled === false ? "停用" : "启用"}
                                        </Tag>
                                    </div>
                                    <div className="mt-1 min-w-0">
                                        <CopyableIdentifier
                                            className="max-w-full"
                                            display={
                                                <span className="min-w-0 truncate" title={tool.key}>
                                                    {tool.key}
                                                </span>
                                            }
                                            quiet
                                            value={tool.key}
                                        />
                                    </div>
                                </div>
                            </div>
                            <p className="m-0 line-clamp-3 min-h-15 text-sm leading-6 text-[var(--td-text-color-secondary)]">
                                {tool.description?.trim() || "暂无说明。"}
                            </p>
                            <div className="mt-auto border-t border-[var(--td-border-level-1-color)] pt-3 text-xs text-[var(--td-text-color-placeholder)]">
                                由运行时能力注册表提供，只读
                            </div>
                        </article>
                    </Card>
                ))}
            </div>
        </div>
    );
}
