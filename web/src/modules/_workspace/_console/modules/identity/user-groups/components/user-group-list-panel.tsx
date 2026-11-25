"use client";

import type { ReactNode } from "react";
import { useMemo, useState } from "react";

import type { UserGroup } from "@pudans-dev/api-client";
import { AddIcon, LockOnIcon, SearchIcon, SecuredIcon, UsergroupIcon } from "tdesign-icons-react";
import { Button, Empty, Input, Skeleton } from "tdesign-react";

import { ScrollArea } from "@/components/ui";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { isBuiltinUserGroup, userGroupDescription, userGroupDisplayName } from "../composables/_user-group";

type UserGroupListPanelProps = {
    error?: unknown;
    groups: UserGroup[];
    loading: boolean;
    onCreate?: () => void;
    onRetry?: () => void;
    onSelect: (groupName: string) => void;
    selectedName?: string;
};

export function UserGroupListPanel({
    error,
    groups,
    loading,
    onCreate,
    onRetry,
    onSelect,
    selectedName,
}: UserGroupListPanelProps) {
    const [keyword, setKeyword] = useState("");

    const { builtinGroups, customGroups, visibleCount } = useMemo(() => {
        const query = keyword.trim().toLocaleLowerCase();
        const matches = groups.filter((group) => {
            if (!query) {
                return true;
            }
            return [userGroupDisplayName(group), group.metadata.name, userGroupDescription(group)]
                .join(" ")
                .toLocaleLowerCase()
                .includes(query);
        });

        return {
            builtinGroups: matches.filter(isBuiltinUserGroup),
            customGroups: matches.filter((group) => !isBuiltinUserGroup(group)),
            visibleCount: matches.length,
        };
    }, [groups, keyword]);

    return (
        <div className="flex min-w-0 flex-col">
            <div className="flex min-w-0 items-start justify-between gap-2">
                <div className="min-w-0">
                    <div className="text-base font-semibold tracking-tight">用户组</div>
                    <div className="mt-1 text-xs text-[var(--td-text-color-secondary)]">
                        {visibleCount} / {groups.length} 个用户组
                    </div>
                </div>
                {onCreate ? (
                    <Button
                        aria-label="新建用户组"
                        icon={<AddIcon />}
                        onClick={onCreate}
                        shape="square"
                        theme="primary"
                    />
                ) : null}
            </div>
            <Input
                className="mt-4"
                clearable
                onChange={(value) => setKeyword(String(value))}
                placeholder="搜索用户组名称或标识"
                prefixIcon={<SearchIcon />}
                value={keyword}
            />

            <ScrollArea className="mt-4 min-h-[260px] lg:max-h-[calc(100vh-250px)]">
                {loading ? (
                    <div className="flex flex-col gap-2">
                        {Array.from({ length: 6 }).map((_, index) => (
                            <Skeleton animation="gradient" key={index} theme="avatar-text" />
                        ))}
                    </div>
                ) : error ? (
                    <div className="flex flex-col items-center gap-3 px-2 py-8 text-center">
                        <span className="text-xs text-[var(--td-text-color-secondary)]">
                            {consoleErrorMessage(error)}
                        </span>
                        {onRetry ? (
                            <Button onClick={onRetry} size="small" variant="outline">
                                重试
                            </Button>
                        ) : null}
                    </div>
                ) : visibleCount === 0 ? (
                    <div className="py-10">
                        <Empty description={keyword ? "没有匹配的用户组" : "暂无用户组"} />
                    </div>
                ) : (
                    <>
                        <UserGroupSection
                            count={builtinGroups.length}
                            groups={builtinGroups}
                            icon={<SecuredIcon />}
                            label="系统内置"
                            onSelect={onSelect}
                            selectedName={selectedName}
                        />
                        <UserGroupSection
                            count={customGroups.length}
                            groups={customGroups}
                            icon={<UsergroupIcon />}
                            label="自定义"
                            onSelect={onSelect}
                            selectedName={selectedName}
                        />
                    </>
                )}
            </ScrollArea>
        </div>
    );
}

type UserGroupSectionProps = {
    count: number;
    groups: UserGroup[];
    icon: ReactNode;
    label: string;
    onSelect: (groupName: string) => void;
    selectedName?: string;
};

function UserGroupSection({ count, groups, icon, label, onSelect, selectedName }: UserGroupSectionProps) {
    return (
        <section className="mb-3 last:mb-0">
            <div className="mb-1.5 flex items-center gap-2 px-2 py-1">
                <span className="flex size-6 items-center justify-center rounded-lg bg-[var(--td-bg-color-component)] text-[var(--td-text-color-secondary)]">
                    {icon}
                </span>
                <span className="text-xs font-medium text-[var(--td-text-color-secondary)]">{label}</span>
                <span className="rounded-full bg-[var(--td-bg-color-component)] px-1.5 text-[11px] text-[var(--td-text-color-placeholder)] tabular-nums">
                    {count}
                </span>
            </div>
            {groups.length === 0 ? (
                <div className="px-2 py-2 text-center text-xs text-[var(--td-text-color-placeholder)]">暂无用户组</div>
            ) : (
                <div className="flex flex-col gap-1">
                    {groups.map((group) => (
                        <UserGroupListItem
                            group={group}
                            key={group.metadata.name}
                            onSelect={onSelect}
                            selected={selectedName === group.metadata.name}
                        />
                    ))}
                </div>
            )}
        </section>
    );
}

type UserGroupListItemProps = {
    group: UserGroup;
    onSelect: (groupName: string) => void;
    selected: boolean;
};

function UserGroupListItem({ group, onSelect, selected }: UserGroupListItemProps) {
    const builtin = isBuiltinUserGroup(group);

    return (
        <Button
            block
            className={`!h-auto !justify-start !rounded-xl !px-2 !py-2 ${
                selected ? "!bg-[var(--td-brand-color-light)] !text-[var(--td-text-color-primary)]" : ""
            }`}
            onClick={() => onSelect(group.metadata.name)}
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
                    <UsergroupIcon size={16} />
                </span>
                <span className="flex min-w-0 flex-1 items-center gap-1.5">
                    <span className="truncate text-sm font-medium">{userGroupDisplayName(group)}</span>
                    {builtin ? (
                        <LockOnIcon className="shrink-0 text-[var(--td-text-color-placeholder)]" size={12} />
                    ) : null}
                </span>
            </span>
        </Button>
    );
}
