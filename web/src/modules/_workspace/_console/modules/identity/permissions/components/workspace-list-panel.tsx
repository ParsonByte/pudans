"use client";

import { useMemo, useState } from "react";

import { BuildingIcon, SearchIcon } from "tdesign-icons-react";
import { Button, Empty, Input } from "tdesign-react";

import { MenuIcon, ScrollArea } from "@/components/ui";

import { type PermissionWorkspaceGroup, matchesWorkspace } from "../composables/_permission-directory";

type WorkspaceListPanelProps = {
    groups: PermissionWorkspaceGroup[];
    onSelect: (workspaceKey: string) => void;
    selectedKey?: string;
};

export function WorkspaceListPanel({ groups, onSelect, selectedKey }: WorkspaceListPanelProps) {
    const [keyword, setKeyword] = useState("");
    const visibleGroups = useMemo(() => {
        const query = keyword.trim().toLocaleLowerCase();
        return query ? groups.filter((group) => matchesWorkspace(group, query)) : groups;
    }, [groups, keyword]);

    return (
        <div className="flex min-w-0 flex-col">
            <div className="min-w-0">
                <div className="text-base font-semibold tracking-tight">工作区</div>
                <div className="mt-1 text-xs text-[var(--td-text-color-secondary)]">
                    {visibleGroups.length} / {groups.length} 个工作区
                </div>
            </div>
            <Input
                className="mt-4"
                clearable
                onChange={(value) => setKeyword(String(value))}
                placeholder="搜索工作区"
                prefixIcon={<SearchIcon />}
                value={keyword}
            />

            <ScrollArea className="mt-4 min-h-[260px] lg:max-h-[calc(100vh-250px)]">
                {visibleGroups.length === 0 ? (
                    <div className="py-10">
                        <Empty description={keyword ? "没有匹配的工作区" : "暂无工作区"} />
                    </div>
                ) : (
                    <section>
                        <div className="mb-1.5 flex items-center gap-2 px-2 py-1">
                            <span className="flex size-6 items-center justify-center rounded-lg bg-[var(--td-bg-color-component)] text-[var(--td-text-color-secondary)]">
                                <BuildingIcon />
                            </span>
                            <span className="text-xs font-medium text-[var(--td-text-color-secondary)]">
                                全部工作区
                            </span>
                            <span className="rounded-full bg-[var(--td-bg-color-component)] px-1.5 text-[11px] text-[var(--td-text-color-placeholder)] tabular-nums">
                                {visibleGroups.length}
                            </span>
                        </div>
                        <div className="flex flex-col gap-1">
                            {visibleGroups.map((group) => (
                                <WorkspaceListItem
                                    group={group}
                                    key={group.key}
                                    onSelect={onSelect}
                                    selected={selectedKey === group.key}
                                />
                            ))}
                        </div>
                    </section>
                )}
            </ScrollArea>
        </div>
    );
}

function WorkspaceListItem({
    group,
    onSelect,
    selected,
}: {
    group: PermissionWorkspaceGroup;
    onSelect: (workspaceKey: string) => void;
    selected: boolean;
}) {
    return (
        <Button
            block
            className={`!h-auto !justify-start !rounded-xl !px-2 !py-2 ${
                selected ? "!bg-[var(--td-brand-color-light)] !text-[var(--td-text-color-primary)]" : ""
            }`}
            onClick={() => onSelect(group.key)}
            style={{ textAlign: "left" }}
            variant="text"
        >
            <span className="flex w-full min-w-0 items-center gap-2.5 text-left">
                <span
                    className={`flex size-8 shrink-0 items-center justify-center rounded-lg ${
                        selected
                            ? "bg-[var(--td-brand-color)] text-[var(--td-text-color-anti)]"
                            : "bg-[var(--td-bg-color-component)] text-[var(--td-text-color-secondary)]"
                    }`}
                >
                    <MenuIcon icon={group.workspace.icon} size={16} />
                </span>
                <span className="min-w-0 flex-1 truncate text-sm font-medium">{group.name}</span>
                <span className="shrink-0 text-[11px] text-[var(--td-text-color-placeholder)] tabular-nums">
                    {group.menuCount}
                </span>
            </span>
        </Button>
    );
}
