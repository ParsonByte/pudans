"use client";

import { useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { FileSettingIcon, RefreshIcon, SearchIcon } from "tdesign-icons-react";
import { Button, Input, Select, Tabs, Tag } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { ConsoleTable } from "../../../components/console-table";
import { EmptyValue } from "../../../components/empty-value";
import { formatCountOrDash, formatDateTime } from "../../../composables/_format";
import { DansPage } from "../components/page";
import { type AiToolDescriptor, type CatalogEntry, listAiTools, listCatalogEntries } from "../composables/_dans";

const CATALOG_TABS = [
    { label: "目录项", value: "entries" },
    { label: "AI 工具", value: "tools" },
] as const;

type CatalogTab = (typeof CATALOG_TABS)[number]["value"];

const CATALOG_KINDS = [
    { label: "全部目录", value: "all" },
    { label: "创作媒体模式", value: "creation_media_mode" },
    { label: "创作操作", value: "creation_operation" },
    { label: "创作参数", value: "creation_parameter_option" },
    { label: "理解维度", value: "understanding_dimension" },
    { label: "记忆类型", value: "memory_type" },
    { label: "记忆层级", value: "memory_layer" },
    { label: "资产类型", value: "asset_kind" },
    { label: "资产来源", value: "asset_source" },
    { label: "沙箱提供商", value: "sandbox_provider" },
    { label: "沙箱模板", value: "sandbox_template" },
    { label: "沙箱资源规格", value: "sandbox_resource_profile" },
    { label: "聊天回答模式", value: "chat_answer_mode" },
    { label: "聊天推理等级", value: "chat_reasoning_level" },
    { label: "探索分类", value: "explore_category" },
    { label: "探索筛选", value: "explore_filter" },
] as const;

function catalogKindLabel(kind?: string) {
    return CATALOG_KINDS.find((item) => item.value === kind)?.label ?? kind ?? "—";
}

export function AiCatalogView() {
    const [tab, setTab] = useState<CatalogTab>("entries");
    const [kind, setKind] = useState("all");
    const [keyword, setKeyword] = useState("");
    const entriesQuery = useQuery({
        queryFn: () => listCatalogEntries(kind === "all" ? undefined : kind),
        queryKey: ["dans", "ai", "catalog-entries", kind],
    });
    const toolsQuery = useQuery({
        queryFn: listAiTools,
        queryKey: ["dans", "ai", "catalog-tools"],
    });
    const entries = entriesQuery.data ?? [];
    const tools = toolsQuery.data ?? [];
    const trimmedKeyword = keyword.trim().toLowerCase();
    const filteredEntries = trimmedKeyword
        ? entries.filter((entry) =>
              [entry.entryKey, entry.label, entry.description].some((value) =>
                  value?.toLowerCase().includes(trimmedKeyword),
              ),
          )
        : entries;
    const columns: TableProps<CatalogEntry>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="font-medium">{row.label ?? row.entryKey ?? <EmptyValue />}</span>
                    <span className="truncate font-mono text-xs text-[var(--td-text-color-secondary)]">
                        {row.entryKey ?? row.id}
                    </span>
                </div>
            ),
            colKey: "label",
            title: "目录项",
        },
        { cell: ({ row }) => catalogKindLabel(row.catalogKind), colKey: "catalogKind", title: "目录类型" },
        {
            cell: ({ row }) => (
                <span className="line-clamp-2 max-w-[360px] text-sm">{row.description ?? <EmptyValue />}</span>
            ),
            colKey: "description",
            title: "说明",
        },
        {
            cell: ({ row }) => (
                <Tag theme={row.enabled === false ? "default" : "success"} variant="light">
                    {row.enabled === false ? "停用" : "启用"}
                </Tag>
            ),
            colKey: "enabled",
            title: "状态",
        },
        { cell: ({ row }) => row.sortOrder ?? <EmptyValue />, colKey: "sortOrder", title: "排序" },
        {
            cell: ({ row }) =>
                row.validTo ? `${formatDateTime(row.validFrom)} 至 ${formatDateTime(row.validTo)}` : "长期有效",
            colKey: "validFrom",
            title: "有效期",
        },
    ];
    const toolColumns: TableProps<AiToolDescriptor>["columns"] = [
        {
            cell: ({ row }) => (
                <div className="flex min-w-0 flex-col gap-1">
                    <span className="font-medium">{row.label ?? row.key}</span>
                    <span className="truncate font-mono text-xs text-[var(--td-text-color-secondary)]">{row.key}</span>
                </div>
            ),
            colKey: "key",
            title: "工具",
        },
        {
            cell: ({ row }) => (
                <span className="line-clamp-2 max-w-[420px] text-sm">{row.description ?? <EmptyValue />}</span>
            ),
            colKey: "description",
            title: "说明",
        },
        {
            cell: ({ row }) => (
                <Tag theme={row.enabled === false ? "default" : "success"} variant="light">
                    {row.enabled === false ? "停用" : "启用"}
                </Tag>
            ),
            colKey: "enabled",
            title: "状态",
        },
    ];
    const activeQuery = tab === "entries" ? entriesQuery : toolsQuery;
    const refresh = () => {
        void entriesQuery.refetch();
        void toolsQuery.refetch();
    };

    return (
        <DansPage
            actions={
                <Button icon={<RefreshIcon />} loading={activeQuery.isFetching} onClick={refresh} variant="outline">
                    刷新
                </Button>
            }
            description="能力目录由 catalogentries 提供，AI 工具来自运行时能力注册表，覆盖聊天、创作、工具、记忆、沙箱和探索等目录类型。"
            icon={<FileSettingIcon size={22} />}
            metrics={[
                { label: "当前目录项", value: formatCountOrDash(entriesQuery.data?.length) },
                { label: "筛选结果", value: formatCountOrDash(filteredEntries.length) },
                { label: "AI 工具", value: formatCountOrDash(toolsQuery.data?.length) },
            ]}
            section="AI"
            title="能力目录"
        >
            <Tabs
                list={CATALOG_TABS.map((item) => ({ label: item.label, value: item.value }))}
                onChange={(value) => setTab(String(value) as CatalogTab)}
                value={tab}
            />
            {tab === "entries" ? (
                <ConsoleTable
                    columns={columns}
                    data={filteredEntries}
                    error={entriesQuery.error}
                    loading={entriesQuery.isPending}
                    onRetry={() => void entriesQuery.refetch()}
                    rowKey="id"
                    title="目录项"
                    toolbar={
                        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                            <Select
                                className="w-full sm:w-[220px]"
                                onChange={(value) => setKind(String(value))}
                                options={CATALOG_KINDS.map((item) => ({ label: item.label, value: item.value }))}
                                value={kind}
                            />
                            <Input
                                className="w-full sm:w-[280px]"
                                clearable
                                onChange={(value) => setKeyword(value)}
                                placeholder="搜索目录名称、键或说明"
                                prefixIcon={<SearchIcon />}
                                value={keyword}
                            />
                        </div>
                    }
                />
            ) : (
                <ConsoleTable
                    columns={toolColumns}
                    data={tools}
                    error={toolsQuery.error}
                    loading={toolsQuery.isPending}
                    onRetry={() => void toolsQuery.refetch()}
                    rowKey="key"
                    title="AI 工具"
                />
            )}
        </DansPage>
    );
}
