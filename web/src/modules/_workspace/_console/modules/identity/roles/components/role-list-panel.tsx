"use client";

import { useMemo, useState } from "react";

import type { Role } from "@pudans-dev/api-client";
import { AddIcon, LockOnIcon, SearchIcon, SecuredIcon, UserBusinessIcon } from "tdesign-icons-react";
import { Button, Card, Empty, Input, Skeleton, Tag } from "tdesign-react";

import { isBuiltinRole, roleDescription, roleDisplayName } from "../../composables/_role";

type RoleListPanelProps = {
    loading: boolean;
    onCreate?: () => void;
    onSelect: (roleName: string) => void;
    roles: Role[];
    selectedName?: string;
};

export function RoleListPanel({ loading, onCreate, onSelect, roles, selectedName }: RoleListPanelProps) {
    const [keyword, setKeyword] = useState("");

    const { builtinRoles, customRoles, visibleCount } = useMemo(() => {
        const query = keyword.trim().toLocaleLowerCase();
        const matches = roles.filter((role) => {
            if (!query) {
                return true;
            }
            return [roleDisplayName(role), role.metadata.name, roleDescription(role)]
                .join(" ")
                .toLocaleLowerCase()
                .includes(query);
        });

        return {
            builtinRoles: matches.filter(isBuiltinRole),
            customRoles: matches.filter((role) => !isBuiltinRole(role)),
            visibleCount: matches.length,
        };
    }, [keyword, roles]);

    return (
        <Card className="h-full min-w-0" bodyClassName="!p-0">
            <div className="border-b border-[var(--td-component-border)] px-4 pt-4">
                <div className="min-w-0">
                    <div className="text-base font-semibold tracking-tight">角色</div>
                    <div className="mt-1 text-xs text-[var(--td-text-color-secondary)]">
                        {visibleCount} / {roles.length} 个角色
                    </div>
                </div>
                <Input
                    className="mt-3 mb-4"
                    clearable
                    onChange={(value) => setKeyword(String(value))}
                    placeholder="搜索角色名称或标识"
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
                        <Empty description={keyword ? "没有匹配的角色" : "暂无角色"} />
                    </div>
                ) : (
                    <>
                        <RoleSection
                            count={builtinRoles.length}
                            icon={<SecuredIcon />}
                            label="系统内置"
                            onSelect={onSelect}
                            roles={builtinRoles}
                            selectedName={selectedName}
                        />
                        <RoleSection
                            count={customRoles.length}
                            emptyAction={
                                onCreate ? (
                                    <Button icon={<AddIcon />} onClick={onCreate} size="small" variant="text">
                                        创建第一个自定义角色
                                    </Button>
                                ) : null
                            }
                            icon={<UserBusinessIcon />}
                            label="自定义"
                            onSelect={onSelect}
                            roles={customRoles}
                            selectedName={selectedName}
                        />
                    </>
                )}
            </div>
        </Card>
    );
}

type RoleSectionProps = {
    count: number;
    emptyAction?: React.ReactNode;
    icon: React.ReactNode;
    label: string;
    onSelect: (roleName: string) => void;
    roles: Role[];
    selectedName?: string;
};

function RoleSection({ count, emptyAction, icon, label, onSelect, roles, selectedName }: RoleSectionProps) {
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
            {roles.length === 0 ? (
                <div className="px-2 py-2 text-center text-xs text-[var(--td-text-color-placeholder)]">
                    {emptyAction ?? "暂无角色"}
                </div>
            ) : (
                <div className="flex flex-col gap-1">
                    {roles.map((role) => (
                        <RoleListItem
                            key={role.metadata.name}
                            onSelect={onSelect}
                            role={role}
                            selected={selectedName === role.metadata.name}
                        />
                    ))}
                </div>
            )}
        </section>
    );
}

type RoleListItemProps = {
    onSelect: (roleName: string) => void;
    role: Role;
    selected: boolean;
};

function RoleListItem({ onSelect, role, selected }: RoleListItemProps) {
    const builtin = isBuiltinRole(role);

    return (
        <Button
            block
            className={`!h-auto !justify-start !rounded-2xl !px-2.5 !py-2.5 ${
                selected ? "!bg-[var(--td-brand-color-light)] !text-[var(--td-text-color-primary)]" : ""
            }`}
            onClick={() => onSelect(role.metadata.name)}
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
                    {builtin ? <SecuredIcon size={16} /> : <UserBusinessIcon size={16} />}
                </span>
                <span className="min-w-0 flex-1">
                    <span className="flex min-w-0 items-center gap-1.5">
                        <span className="truncate text-sm font-medium">{roleDisplayName(role)}</span>
                        {builtin ? (
                            <LockOnIcon className="shrink-0 text-[var(--td-text-color-placeholder)]" size={12} />
                        ) : null}
                    </span>
                    <span className="mt-0.5 block truncate font-mono text-[11px] text-[var(--td-text-color-placeholder)]">
                        {role.metadata.name}
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
