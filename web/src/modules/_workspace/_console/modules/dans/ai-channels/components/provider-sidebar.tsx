"use client";

import { AddIcon, RefreshIcon, SearchIcon } from "tdesign-icons-react";
import { Button, Input, Skeleton, Tooltip } from "tdesign-react";

import { ProviderMark } from "../../components/provider-mark";
import type { AiChannel, AiProviderDescriptor } from "../../composables/_dans";

export type ProviderEntry = {
    channel?: AiChannel;
    descriptor: AiProviderDescriptor;
};

type ProviderSidebarProps = {
    entries: ProviderEntry[];
    loading: boolean;
    onAdd: () => void;
    onRefresh: () => Promise<void>;
    onSearchChange: (keyword: string) => void;
    onSelect: (provider: string) => void;
    onShowAll: () => void;
    search: string;
    selectedProvider?: string;
    refreshing: boolean;
};

export function isProviderEnabled(entry: ProviderEntry) {
    return entry.channel?.status === "active";
}

export function matchesProviderSearch(entry: ProviderEntry, search: string) {
    const normalized = search.trim().toLowerCase();
    if (!normalized) {
        return true;
    }

    return [entry.descriptor.displayName, entry.descriptor.provider].some((value) =>
        value.toLowerCase().includes(normalized),
    );
}

type ProviderRowProps = {
    entry: ProviderEntry;
    onSelect: (provider: string) => void;
    selected: boolean;
};

function ProviderRow({ entry, onSelect, selected }: ProviderRowProps) {
    const enabled = isProviderEnabled(entry);

    return (
        <button
            className={`flex w-full min-w-0 items-center gap-2.5 rounded-lg px-2.5 py-2 text-left transition-colors ${
                selected
                    ? "bg-[var(--td-bg-color-secondarycontainer)] font-medium text-[var(--td-text-color-primary)]"
                    : "text-[var(--td-text-color-secondary)] hover:bg-[var(--td-bg-color-secondarycontainer)] hover:text-[var(--td-text-color-primary)]"
            }`}
            onClick={() => onSelect(entry.descriptor.provider)}
            type="button"
        >
            <ProviderMark logoUrl={entry.descriptor.logoUrl} provider={entry.descriptor.provider} size="small" />
            <span className="min-w-0 flex-1 truncate text-sm">{entry.descriptor.displayName}</span>
            {enabled ? (
                <span aria-label="已启用" className="size-2 shrink-0 rounded-full bg-[var(--td-success-color)]" />
            ) : null}
        </button>
    );
}

type ProviderGroupProps = {
    emptyText: string;
    entries: ProviderEntry[];
    label: string;
    onSelect: (provider: string) => void;
    selectedProvider?: string;
};

function ProviderGroup({ emptyText, entries, label, onSelect, selectedProvider }: ProviderGroupProps) {
    return (
        <section className="flex flex-col gap-1">
            <div className="flex items-center gap-1.5 px-2 py-2 text-xs font-medium text-[var(--td-text-color-secondary)]">
                <span>{label}</span>
                <span className="rounded bg-[var(--td-bg-color-secondarycontainer)] px-1.5 py-0.5">
                    {entries.length}
                </span>
            </div>
            {entries.length === 0 ? (
                <p className="m-0 px-2.5 py-1 text-xs text-[var(--td-text-color-placeholder)]">{emptyText}</p>
            ) : (
                entries.map((entry) => (
                    <ProviderRow
                        entry={entry}
                        key={entry.descriptor.provider}
                        onSelect={onSelect}
                        selected={selectedProvider === entry.descriptor.provider}
                    />
                ))
            )}
        </section>
    );
}

export function ProviderSidebar({
    entries,
    loading,
    onAdd,
    onSearchChange,
    onRefresh,
    onSelect,
    onShowAll,
    refreshing,
    search,
    selectedProvider,
}: ProviderSidebarProps) {
    const filteredEntries = entries.filter((entry) => matchesProviderSearch(entry, search));
    const enabledEntries = filteredEntries.filter(isProviderEnabled);
    const disabledEntries = filteredEntries.filter((entry) => !isProviderEnabled(entry));

    return (
        <aside className="flex min-h-0 min-w-0 flex-col gap-3 border-b border-[var(--td-border-level-1-color)] p-3 lg:border-r lg:border-b-0">
            <div className="flex items-center gap-2">
                <Input
                    className="min-w-0 flex-1"
                    clearable
                    onChange={(value) => onSearchChange(value)}
                    placeholder="搜索服务商..."
                    prefixIcon={<SearchIcon />}
                    size="small"
                    value={search}
                />
                <Tooltip content="刷新服务商">
                    <Button
                        aria-label="刷新服务商"
                        icon={<RefreshIcon />}
                        loading={refreshing}
                        onClick={() => void onRefresh()}
                        shape="square"
                        size="small"
                    />
                </Tooltip>
                <Tooltip content="添加服务商">
                    <Button aria-label="添加服务商" icon={<AddIcon />} onClick={onAdd} shape="square" size="small" />
                </Tooltip>
            </div>

            <button
                className={`flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left text-sm transition-colors ${
                    !selectedProvider
                        ? "bg-[var(--td-bg-color-secondarycontainer)] font-medium"
                        : "text-[var(--td-text-color-secondary)] hover:bg-[var(--td-bg-color-secondarycontainer)]"
                }`}
                onClick={onShowAll}
                type="button"
            >
                <span className="min-w-0 flex-1">全部</span>
                <span className="text-xs text-[var(--td-text-color-placeholder)]">{entries.length}</span>
            </button>

            <div className="min-h-0 flex-1 overflow-y-auto">
                {loading ? (
                    <div className="flex flex-col gap-2 p-1">
                        <Skeleton theme="text" />
                        <Skeleton theme="text" />
                        <Skeleton theme="text" />
                    </div>
                ) : (
                    <div className="flex flex-col gap-2">
                        <ProviderGroup
                            emptyText={search.trim() ? "没有匹配的已启用服务商" : "暂无已启用服务商"}
                            entries={enabledEntries}
                            label="已启用"
                            onSelect={onSelect}
                            selectedProvider={selectedProvider}
                        />
                        <ProviderGroup
                            emptyText={search.trim() ? "没有匹配的未启用服务商" : "暂无未启用服务商"}
                            entries={disabledEntries}
                            label="未启用"
                            onSelect={onSelect}
                            selectedProvider={selectedProvider}
                        />
                    </div>
                )}
            </div>
        </aside>
    );
}
