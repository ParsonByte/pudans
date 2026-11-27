"use client";

import { useState } from "react";

import { Switch } from "tdesign-react";

import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";

import { ProviderMark } from "../../components/provider-mark";
import { type ProviderEntry, isProviderEnabled, matchesProviderSearch } from "./provider-sidebar";

type ProviderOverviewProps = {
    entries: ProviderEntry[];
    onSelect: (provider: string) => void;
    onToggle: (entry: ProviderEntry, enabled: boolean) => Promise<void>;
    search: string;
};

type ProviderCardProps = {
    entry: ProviderEntry;
    onSelect: (provider: string) => void;
    onToggle: (entry: ProviderEntry, enabled: boolean) => Promise<void>;
};

function ProviderCard({ entry, onSelect, onToggle }: ProviderCardProps) {
    const prompt = useMessagePrompt();
    const [pending, setPending] = useState(false);
    const enabled = isProviderEnabled(entry);
    const configured = Boolean(entry.channel);
    const displayName = entry.descriptor.displayName;

    const toggle = async (value: boolean | string) => {
        if (typeof value !== "boolean") {
            return;
        }
        setPending(true);
        try {
            await onToggle(entry, value);
        } catch (error) {
            prompt.error({ content: consoleErrorMessage(error, "渠道状态更新失败。") });
        } finally {
            setPending(false);
        }
    };

    return (
        <article className="group relative flex min-h-[176px] min-w-0 flex-col overflow-hidden rounded-xl border border-[var(--td-border-level-1-color)] bg-[var(--td-bg-color-container)] transition-colors hover:border-[var(--td-brand-color)]">
            <button
                className="flex flex-1 items-start gap-3 p-4 text-left"
                onClick={() => onSelect(entry.descriptor.provider)}
                type="button"
            >
                <ProviderMark logoUrl={entry.descriptor.logoUrl} provider={entry.descriptor.provider} size="medium" />
                <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold">{displayName}</span>
                    <span className="mt-1 line-clamp-2 block text-xs leading-5 text-[var(--td-text-color-placeholder)]">
                        {entry.descriptor.description || `配置 ${displayName} 渠道后即可使用模型。`}
                    </span>
                </span>
            </button>
            <footer className="mt-auto flex items-center justify-end border-t border-[var(--td-border-level-1-color)] px-4 py-3">
                {configured ? (
                    <span onClick={(event) => event.stopPropagation()}>
                        <Switch
                            aria-label={`${displayName} 渠道状态`}
                            loading={pending}
                            onChange={(value) => void toggle(value)}
                            size="small"
                            value={enabled}
                        />
                    </span>
                ) : (
                    <button
                        className="text-xs text-[var(--td-brand-color)] hover:underline"
                        onClick={() => onSelect(entry.descriptor.provider)}
                        type="button"
                    >
                        配置
                    </button>
                )}
            </footer>
        </article>
    );
}

function ProviderSection({
    emptyText,
    entries,
    label,
    onSelect,
    onToggle,
}: {
    emptyText: string;
    entries: ProviderEntry[];
    label: string;
    onSelect: (provider: string) => void;
    onToggle: (entry: ProviderEntry, enabled: boolean) => Promise<void>;
}) {
    return (
        <section className="flex min-w-0 flex-col gap-3">
            <div className="flex items-center gap-2">
                <h2 className="m-0 text-base font-semibold">{label}</h2>
                <span className="rounded-md bg-[var(--td-bg-color-secondarycontainer)] px-1.5 py-0.5 text-xs text-[var(--td-text-color-secondary)]">
                    {entries.length}
                </span>
            </div>
            {entries.length === 0 ? (
                <p className="m-0 rounded-xl border border-dashed border-[var(--td-border-level-1-color)] px-4 py-6 text-center text-xs text-[var(--td-text-color-placeholder)]">
                    {emptyText}
                </p>
            ) : (
                <div className="grid min-w-0 grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
                    {entries.map((entry) => (
                        <ProviderCard
                            entry={entry}
                            key={entry.descriptor.provider}
                            onSelect={onSelect}
                            onToggle={onToggle}
                        />
                    ))}
                </div>
            )}
        </section>
    );
}

export function ProviderOverview({ entries, onSelect, onToggle, search }: ProviderOverviewProps) {
    const filteredEntries = entries.filter((entry) => matchesProviderSearch(entry, search));
    const enabledEntries = filteredEntries.filter(isProviderEnabled);
    const disabledEntries = filteredEntries.filter((entry) => !isProviderEnabled(entry));

    return (
        <div className="flex min-w-0 flex-col gap-7 p-4 lg:p-6">
            <ProviderSection
                emptyText={search.trim() ? "没有匹配的已启用服务商" : "暂无已启用服务商"}
                entries={enabledEntries}
                label="已启用服务商"
                onSelect={onSelect}
                onToggle={onToggle}
            />
            <ProviderSection
                emptyText={search.trim() ? "没有匹配的未启用服务商" : "暂无未启用服务商"}
                entries={disabledEntries}
                label="未启用服务商"
                onSelect={onSelect}
                onToggle={onToggle}
            />
        </div>
    );
}
