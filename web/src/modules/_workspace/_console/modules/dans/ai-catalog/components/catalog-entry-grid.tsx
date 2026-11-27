"use client";

import { useMemo, useState } from "react";

import {
    AddIcon,
    ChatIcon,
    CloudIcon,
    CompassIcon,
    DataIcon,
    DeleteIcon,
    EditIcon,
    FileSettingIcon,
    FilterIcon,
    FolderIcon,
    ImageIcon,
    LayersIcon,
    MoreIcon,
    SearchIcon,
    SettingIcon,
    ShareIcon,
    TemplateIcon,
    TimeIcon,
    ToolsIcon,
    VideoIcon,
} from "tdesign-icons-react";
import { Alert, Button, Card, Dropdown, Empty, Input, Select, Skeleton, Tag } from "tdesign-react";
import type { DropdownOption } from "tdesign-react";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatCount, formatDateTime } from "../../../../composables/_format";
import type { CatalogEntry } from "../../composables/_dans";
import {
    CATALOG_KIND_OPTIONS,
    CATALOG_STATUS_OPTIONS,
    catalogEntryState,
    catalogEntryStateLabel,
    catalogKindLabel,
} from "../composables/_catalog";

const KIND_ICONS: Record<string, typeof FileSettingIcon> = {
    asset_kind: FolderIcon,
    asset_source: ShareIcon,
    chat_answer_mode: ChatIcon,
    chat_reasoning_level: ChatIcon,
    creation_media_mode: ImageIcon,
    creation_operation: VideoIcon,
    creation_parameter_option: SettingIcon,
    explore_category: CompassIcon,
    explore_filter: FilterIcon,
    memory_layer: LayersIcon,
    memory_type: LayersIcon,
    sandbox_provider: CloudIcon,
    sandbox_resource_profile: DataIcon,
    sandbox_template: TemplateIcon,
    understanding_dimension: ChatIcon,
};

const STATE_THEME = {
    active: "success",
    disabled: "default",
    expired: "danger",
    pending: "warning",
} as const;

type CatalogEntryGridProps = {
    entries: CatalogEntry[];
    error?: unknown;
    loading: boolean;
    onCreate?: () => void;
    onDelete?: (entry: CatalogEntry) => void;
    onEdit?: (entry: CatalogEntry) => void;
    onRetry: () => void;
};

export function CatalogEntryGrid({
    entries,
    error,
    loading,
    onCreate,
    onDelete,
    onEdit,
    onRetry,
}: CatalogEntryGridProps) {
    const [kind, setKind] = useState("all");
    const [keyword, setKeyword] = useState("");
    const [status, setStatus] = useState("all");
    const normalizedKeyword = keyword.trim().toLowerCase();
    const hasFilters = Boolean(keyword) || kind !== "all" || status !== "all";
    const filteredEntries = useMemo(
        () =>
            entries.filter((entry) => {
                if (kind !== "all" && entry.catalogKind !== kind) {
                    return false;
                }
                if (status === "enabled" && entry.enabled === false) {
                    return false;
                }
                if (status === "disabled" && entry.enabled !== false) {
                    return false;
                }
                if (!normalizedKeyword) {
                    return true;
                }

                return [
                    entry.entryKey,
                    entry.label,
                    entry.description,
                    entry.parentKey,
                    catalogKindLabel(entry.catalogKind),
                ].some((value) => value?.toLowerCase().includes(normalizedKeyword));
            }),
        [entries, kind, normalizedKeyword, status],
    );
    const resetFilters = () => {
        setKind("all");
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
                        <Skeleton animation="gradient" rowCol={[1, 2, 1, 2]} />
                    </Card>
                ))}
            </div>
        );
    }

    if (entries.length === 0) {
        return (
            <Card bordered={false}>
                <Empty
                    action={
                        onCreate ? (
                            <Button icon={<AddIcon />} onClick={onCreate} theme="primary">
                                新建目录项
                            </Button>
                        ) : undefined
                    }
                    description="当前还没有可管理的目录项。"
                />
            </Card>
        );
    }

    if (filteredEntries.length === 0) {
        return (
            <Card bordered={false}>
                <Empty
                    action={
                        <Button onClick={resetFilters} variant="outline">
                            清除筛选
                        </Button>
                    }
                    description="没有匹配的目录项。"
                />
            </Card>
        );
    }

    return (
        <div className="flex min-w-0 flex-col gap-4">
            <div className="flex min-w-0 flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                <div className="flex min-w-0 flex-wrap items-center gap-2">
                    <div className="w-full min-w-0 sm:w-[300px]">
                        <Input
                            clearable
                            onChange={(value) => setKeyword(value)}
                            placeholder="搜索目录名称、键或说明"
                            prefixIcon={<SearchIcon />}
                            value={keyword}
                        />
                    </div>
                    <div className="w-full min-w-0 sm:w-[210px]">
                        <Select
                            filterable
                            onChange={(value) => setKind(String(value))}
                            options={CATALOG_KIND_OPTIONS.map((option) => ({ ...option }))}
                            value={kind}
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
                    {filteredEntries.length === entries.length
                        ? `共 ${formatCount(entries.length)} 个目录项`
                        : `筛选出 ${formatCount(filteredEntries.length)} / ${formatCount(entries.length)} 个目录项`}
                </span>
            </div>

            <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-2 2xl:grid-cols-3">
                {filteredEntries.map((entry) => (
                    <CatalogEntryCard
                        entry={entry}
                        key={entry.id}
                        onDelete={onDelete ? () => onDelete(entry) : undefined}
                        onEdit={onEdit ? () => onEdit(entry) : undefined}
                    />
                ))}
            </div>
        </div>
    );
}

function CatalogEntryCard({
    entry,
    onDelete,
    onEdit,
}: {
    entry: CatalogEntry;
    onDelete?: () => void;
    onEdit?: () => void;
}) {
    const Icon = KIND_ICONS[entry.catalogKind ?? ""] ?? FileSettingIcon;
    const state = catalogEntryState(entry);
    const actions: DropdownOption[] = [
        ...(onEdit ? [{ content: "编辑目录项", prefixIcon: <EditIcon size={16} />, value: "edit" }] : []),
        ...(onDelete
            ? [
                  {
                      content: "删除目录项",
                      prefixIcon: <DeleteIcon size={16} />,
                      theme: "error" as const,
                      value: "delete",
                  },
              ]
            : []),
    ];
    const validFrom = entry.validFrom ? formatDateTime(entry.validFrom) : "立即生效";
    const validTo = entry.validTo ? formatDateTime(entry.validTo) : "长期有效";
    const metadataCount = Object.keys(entry.metadata ?? {}).length;

    return (
        <Card bordered className="h-full min-w-0 transition-colors hover:border-[var(--td-brand-color)]">
            <article className="flex h-full min-w-0 flex-col gap-4">
                <div className="flex min-w-0 items-start gap-3">
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-[var(--td-brand-color-light)] text-[var(--td-brand-color)]">
                        <Icon size={20} />
                    </span>
                    <div className="min-w-0 flex-1">
                        <div className="flex min-w-0 items-start justify-between gap-2">
                            <div className="min-w-0">
                                <div className="flex min-w-0 flex-wrap items-center gap-2">
                                    <h3
                                        className="m-0 truncate text-base font-semibold"
                                        title={entry.label ?? entry.entryKey}
                                    >
                                        {entry.label ?? entry.entryKey ?? "未命名目录项"}
                                    </h3>
                                    <Tag theme={STATE_THEME[state]} variant="light">
                                        {catalogEntryStateLabel(state)}
                                    </Tag>
                                </div>
                                <div className="mt-1 min-w-0">
                                    <CopyableIdentifier
                                        className="max-w-full"
                                        display={
                                            <span className="min-w-0 truncate" title={entry.entryKey}>
                                                {entry.entryKey ?? entry.id}
                                            </span>
                                        }
                                        quiet
                                        value={entry.entryKey ?? entry.id}
                                    />
                                </div>
                            </div>
                            {actions.length > 0 ? (
                                <Dropdown
                                    onClick={(item) => {
                                        if (item.value === "edit") {
                                            onEdit?.();
                                        } else if (item.value === "delete") {
                                            onDelete?.();
                                        }
                                    }}
                                    options={actions}
                                    placement="bottom-right"
                                    trigger="click"
                                >
                                    <Button
                                        aria-label={`${entry.label ?? entry.entryKey ?? entry.id} 的目录项操作`}
                                        icon={<MoreIcon />}
                                        shape="square"
                                        size="small"
                                        variant="text"
                                    />
                                </Dropdown>
                            ) : null}
                        </div>
                    </div>
                </div>

                <div className="flex flex-wrap items-center gap-1.5">
                    <Tag theme="primary" variant="light">
                        {catalogKindLabel(entry.catalogKind)}
                    </Tag>
                    {entry.parentKey ? <Tag variant="outline">上级 {entry.parentKey}</Tag> : null}
                </div>

                <p className="m-0 line-clamp-2 min-h-10 text-sm text-[var(--td-text-color-secondary)]">
                    {entry.description?.trim() || "暂无说明。"}
                </p>

                <div className="grid grid-cols-2 gap-3 border-y border-[var(--td-border-level-1-color)] py-3">
                    <CatalogMetric label="排序" value={formatCount(entry.sortOrder ?? 100)} />
                    <CatalogMetric label="上级目录" value={entry.parentKey ?? "顶级目录"} />
                </div>

                <div className="flex min-w-0 items-center gap-1.5 text-xs text-[var(--td-text-color-secondary)]">
                    <TimeIcon className="shrink-0" size={14} />
                    <span className="min-w-0 truncate">
                        {validFrom}
                        <span className="mx-1.5 text-[var(--td-text-color-placeholder)]">至</span>
                        {validTo}
                    </span>
                </div>

                <div className="mt-auto flex items-center justify-between gap-2 border-t border-[var(--td-border-level-1-color)] pt-3 text-xs text-[var(--td-text-color-placeholder)]">
                    <span className="inline-flex items-center gap-1.5">
                        <ToolsIcon size={14} />
                        {metadataCount > 0 ? `${metadataCount} 项扩展属性` : "标准目录项"}
                    </span>
                    <span>更新于 {formatDateTime(entry.updatedAt)}</span>
                </div>
            </article>
        </Card>
    );
}

function CatalogMetric({ label, value }: { label: string; value: string }) {
    return (
        <div className="flex min-w-0 flex-col gap-0.5">
            <span className="text-[11px] text-[var(--td-text-color-secondary)]">{label}</span>
            <span className="truncate font-medium" title={value}>
                {value}
            </span>
        </div>
    );
}
