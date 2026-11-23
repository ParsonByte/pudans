"use client";

import { useMemo, useState } from "react";

import type { UserGroup } from "@pudans-dev/api-client";
import { AddIcon, LockOnIcon, SearchIcon, UsergroupIcon } from "tdesign-icons-react";
import { Button, Card, Empty, Input, Skeleton, Tag } from "tdesign-react";

import { isBuiltinUserGroup, userGroupDescription, userGroupDisplayName } from "../composables/_user-group";

type UserGroupListPanelProps = {
    groups: UserGroup[];
    loading: boolean;
    onCreate?: () => void;
    onSelect: (groupName: string) => void;
    selectedName?: string;
};

export function UserGroupListPanel({ groups, loading, onCreate, onSelect, selectedName }: UserGroupListPanelProps) {
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
        <Card className="h-full min-w-0" bodyClassName="!p-0">
            <div className="border-b border-[var(--td-component-border)] px-4 pt-4">
                <div className="min-w-0">
                    <div className="text-base font-semibold tracking-tight">用户组</div>
                    <div className="mt-1 text-xs text-[var(--td-text-color-secondary)]">
                        {visibleCount} / {groups.length} 个用户组
                    </div>
                </div>
                <Input
                    className="mt-3 mb-4"
                    clearable
                    onChange={(value) => setKeyword(String(value))}
                    placeholder="搜索用户组名称或标识"
                    prefixIcon={<SearchIcon />}
                    size="small"
                    value={keyword}
                />
            </div>

            <div className="max-h-[420px] min-h-[260px] overflow-y-auto p-2 xl:max-h-[calc(100vh-280px)]">
                {loading ? (
                    <div className="flex flex-col gap-2 p-2">
                        {Array.from({ length: 6 }).map((_, index) => (
                            <Skeleton animation="gradient" key={index} theme="avatar-text" />
                        ))}
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
                            label="系统内置"
                            onSelect={onSelect}
                            selectedName={selectedName}
                        />
                        <UserGroupSection
                            count={customGroups.length}
                            emptyAction={
                                onCreate ? (
                                    <Button icon={<AddIcon />} onClick={onCreate} size="small" variant="text">
                                        创建第一个自定义用户组
                                    </Button>
                                ) : null
                            }
                            groups={customGroups}
                            label="自定义"
                            onSelect={onSelect}
                            selectedName={selectedName}
                        />
                    </>
                )}
            </div>
        </Card>
    );
}

type UserGroupSectionProps = {
    count: number;
    emptyAction?: React.ReactNode;
    groups: UserGroup[];
    label: string;
    onSelect: (groupName: string) => void;
    selectedName?: string;
};

function UserGroupSection({ count, emptyAction, groups, label, onSelect, selectedName }: UserGroupSectionProps) {
    return (
        <section className="mb-3 last:mb-0">
            <div className="mb-1.5 flex items-center gap-2 px-2 py-1">
                <span className="flex size-6 items-center justify-center rounded-lg bg-[var(--td-bg-color-component)] text-[var(--td-text-color-secondary)]">
                    <UsergroupIcon />
                </span>
                <span className="text-xs font-medium text-[var(--td-text-color-secondary)]">{label}</span>
                <span className="rounded-full bg-[var(--td-bg-color-component)] px-1.5 text-[11px] text-[var(--td-text-color-placeholder)] tabular-nums">
                    {count}
                </span>
            </div>
            {groups.length === 0 ? (
                <div className="px-2 py-2 text-center text-xs text-[var(--td-text-color-placeholder)]">
                    {emptyAction ?? "暂无用户组"}
                </div>
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
            className={`!h-auto !justify-start !rounded-2xl !px-2.5 !py-2.5 ${
                selected ? "!bg-[var(--td-brand-color-light)] !text-[var(--td-text-color-primary)]" : ""
            }`}
            onClick={() => onSelect(group.metadata.name)}
            style={{ textAlign: "left" }}
            variant="text"
        >
            <span className="flex w-full min-w-0 items-start gap-3 text-left">
                <span
                    className={`mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-xl ${
                        selected
                            ? "bg-[var(--td-brand-color)] text-[var(--td-text-color-anti)]"
                            : "bg-[var(--td-bg-color-component)] text-[var(--td-text-color-secondary)]"
                    }`}
                >
                    <UsergroupIcon size={16} />
                </span>
                <span className="min-w-0 flex-1">
                    <span className="flex min-w-0 items-center gap-1.5">
                        <span className="truncate text-sm font-medium">{userGroupDisplayName(group)}</span>
                        {builtin ? (
                            <LockOnIcon className="shrink-0 text-[var(--td-text-color-placeholder)]" size={12} />
                        ) : null}
                    </span>
                    <span className="mt-0.5 block truncate font-mono text-[11px] text-[var(--td-text-color-placeholder)]">
                        {group.metadata.name}
                    </span>
                </span>
                {builtin ? (
                    <Tag theme="warning" variant="light">
                        内置
                    </Tag>
                ) : null}
            </span>
        </Button>
    );
}
