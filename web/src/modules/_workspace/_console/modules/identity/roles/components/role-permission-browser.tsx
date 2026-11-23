"use client";

import { useMemo, useState } from "react";

import { SearchIcon } from "tdesign-icons-react";
import { Card, Empty, Input, Select } from "tdesign-react";

import { MenuIcon } from "@/components/ui";

import type { RoleMenuEntry } from "../composables/_role-permissions";

type RolePermissionBrowserProps = {
    emptyDescription: string;
    entries: RoleMenuEntry[];
    matchesEntry: (entry: RoleMenuEntry, query: string) => boolean;
    renderGroup: (entries: RoleMenuEntry[]) => React.ReactNode;
    summary: (entries: RoleMenuEntry[]) => string;
    toolbarPlaceholder: string;
};

export function RolePermissionBrowser({
    emptyDescription,
    entries,
    matchesEntry,
    renderGroup,
    summary,
    toolbarPlaceholder,
}: RolePermissionBrowserProps) {
    const [keyword, setKeyword] = useState("");
    const [workspaceId, setWorkspaceId] = useState("all");

    const workspaceOptions = useMemo(() => {
        const workspaces = new Map<string, string>();
        for (const entry of entries) {
            workspaces.set(entry.workspace.id ?? entry.workspace.name ?? "", entry.workspace.name ?? "");
        }
        return Array.from(workspaces.entries()).map(([value, label]) => ({ label, value }));
    }, [entries]);

    const groups = useMemo(() => {
        const query = keyword.trim().toLocaleLowerCase();
        const filtered = entries.filter((entry) => {
            const workspaceKey = entry.workspace.id ?? entry.workspace.name ?? "";
            if (workspaceId !== "all" && workspaceKey !== workspaceId) {
                return false;
            }
            return !query || matchesEntry(entry, query);
        });

        const byWorkspace = new Map<string, { entries: RoleMenuEntry[]; workspaceName: string }>();
        for (const entry of filtered) {
            const key = entry.workspace.id ?? entry.workspace.name ?? "workspace";
            const group = byWorkspace.get(key) ?? {
                entries: [],
                workspaceName: entry.workspace.name ?? key,
            };
            group.entries.push(entry);
            byWorkspace.set(key, group);
        }
        return Array.from(byWorkspace.entries()).map(([key, group]) => ({ key, ...group }));
    }, [entries, keyword, matchesEntry, workspaceId]);

    return (
        <div className="flex flex-col gap-4">
            <Card className="bg-[var(--td-bg-color-page)]">
                <div className="flex flex-col gap-3 xl:flex-row xl:items-center">
                    <Select
                        className="w-full xl:w-48"
                        onChange={(value) => setWorkspaceId(value ? String(value) : "all")}
                        options={[{ label: "全部工作区", value: "all" }, ...workspaceOptions]}
                        value={workspaceId}
                    />
                    <Input
                        className="min-w-0 flex-1"
                        clearable
                        onChange={(value) => setKeyword(String(value))}
                        placeholder={toolbarPlaceholder}
                        prefixIcon={<SearchIcon />}
                        value={keyword}
                    />
                </div>
            </Card>

            {groups.length === 0 ? (
                <Empty description={emptyDescription} />
            ) : (
                <div className="flex flex-col gap-4">
                    {groups.map((group) => (
                        <Card
                            header={
                                <div className="flex min-w-0 items-center gap-3">
                                    <span className="flex size-8 items-center justify-center rounded-lg bg-[var(--td-bg-color-component)] text-[var(--td-text-color-secondary)]">
                                        <MenuIcon icon={group.entries[0]?.workspace.icon} size={16} />
                                    </span>
                                    <span className="min-w-0">
                                        <span className="block truncate text-sm font-medium">
                                            {group.workspaceName}
                                        </span>
                                        <span className="mt-0.5 block truncate text-xs font-normal text-[var(--td-text-color-placeholder)]">
                                            {summary(group.entries)}
                                        </span>
                                    </span>
                                </div>
                            }
                            headerBordered
                            key={group.key}
                        >
                            {renderGroup(group.entries)}
                        </Card>
                    ))}
                </div>
            )}
        </div>
    );
}
