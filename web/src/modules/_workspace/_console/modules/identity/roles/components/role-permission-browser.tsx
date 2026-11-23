"use client";

import { useMemo, useState } from "react";

import { ProCard } from "@ant-design/pro-components";
import { Icon } from "@iconify/react";
import { Empty, Input, Select, theme } from "antd";
import { Search } from "lucide-react";

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
    const { token } = theme.useToken();
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
        <div className="flex flex-col gap-5">
            <ProCard
                className="!rounded-2xl"
                style={{ background: token.colorFillQuaternary, borderColor: token.colorBorderSecondary }}
                styles={{ body: { padding: 16 } }}
            >
                <div className="flex flex-col gap-3 xl:flex-row xl:items-center">
                    <Select
                        className="w-full xl:!w-48"
                        onChange={setWorkspaceId}
                        options={[{ label: "全部工作区", value: "all" }, ...workspaceOptions]}
                        value={workspaceId}
                    />
                    <Input
                        allowClear
                        className="min-w-0 flex-1"
                        onChange={(event) => setKeyword(event.target.value)}
                        placeholder={toolbarPlaceholder}
                        prefix={<Search className="text-neutral-400" size={14} />}
                        value={keyword}
                    />
                </div>
            </ProCard>

            {groups.length === 0 ? (
                <Empty description={emptyDescription} image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
                <div className="flex flex-col gap-4">
                    {groups.map((group) => (
                        <ProCard
                            className="!rounded-2xl"
                            headerBordered
                            key={group.key}
                            styles={{
                                body: { padding: 20 },
                                header: { paddingBlock: 14, paddingInline: 20 },
                            }}
                            title={
                                <div className="flex min-w-0 items-center gap-3">
                                    <span
                                        className="flex size-8 items-center justify-center rounded-lg"
                                        style={{
                                            background: token.colorFillTertiary,
                                            color: token.colorTextSecondary,
                                        }}
                                    >
                                        <Icon
                                            icon={group.entries[0]?.workspace.icon || "lucide:layout-dashboard"}
                                            width={16}
                                        />
                                    </span>
                                    <span className="min-w-0">
                                        <span className="block truncate text-sm font-medium">
                                            {group.workspaceName}
                                        </span>
                                        <span
                                            className="mt-0.5 block truncate text-xs font-normal"
                                            style={{ color: token.colorTextTertiary }}
                                        >
                                            {summary(group.entries)}
                                        </span>
                                    </span>
                                </div>
                            }
                        >
                            {renderGroup(group.entries)}
                        </ProCard>
                    ))}
                </div>
            )}
        </div>
    );
}
