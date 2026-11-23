"use client";

import { useMemo, useState } from "react";

import { ProCard } from "@ant-design/pro-components";
import type { Role } from "@pudans-dev/api-client";
import { Button, Empty, Input, Tag, theme } from "antd";
import { LockKeyhole, Plus, Search, Shield, ShieldCheck } from "lucide-react";

import { isBuiltinRole, roleDescription, roleDisplayName } from "../../composables/_role";

type RoleListPanelProps = {
    canCreate: boolean;
    loading: boolean;
    onCreate: () => void;
    onSelect: (roleName: string) => void;
    roles: Role[];
    selectedName?: string;
};

export function RoleListPanel({ canCreate, loading, onCreate, onSelect, roles, selectedName }: RoleListPanelProps) {
    const { token } = theme.useToken();
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
        <ProCard
            className="h-full min-w-0 overflow-hidden !rounded-[24px]"
            styles={{ body: { padding: 0 } }}
            style={{
                background: token.colorBgContainer,
                borderColor: token.colorBorderSecondary,
                boxShadow: token.boxShadowTertiary,
            }}
        >
            <div className="border-b px-4 pt-4" style={{ borderColor: token.colorBorderSecondary }}>
                <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                        <div className="text-base font-semibold tracking-tight">角色</div>
                        <div className="mt-1 text-xs" style={{ color: token.colorTextSecondary }}>
                            {visibleCount} / {roles.length} 个角色
                        </div>
                    </div>
                    {canCreate ? (
                        <Button icon={<Plus size={14} />} onClick={onCreate} size="small" type="primary">
                            新建
                        </Button>
                    ) : null}
                </div>
                <Input
                    allowClear
                    className="mt-3 mb-4"
                    onChange={(event) => setKeyword(event.target.value)}
                    placeholder="搜索角色名称或标识"
                    prefix={<Search className="text-neutral-400" size={14} />}
                    size="small"
                    value={keyword}
                />
            </div>

            <div className="max-h-[420px] min-h-[260px] overflow-y-auto p-2 xl:max-h-[calc(100vh-280px)]">
                {loading ? (
                    <div className="space-y-2 p-2">
                        {Array.from({ length: 6 }).map((_, index) => (
                            <div
                                className="h-16 animate-pulse rounded-2xl"
                                key={index}
                                style={{ background: token.colorFillQuaternary }}
                            />
                        ))}
                    </div>
                ) : visibleCount === 0 ? (
                    <Empty
                        className="py-10"
                        description={keyword ? "没有匹配的角色" : "暂无角色"}
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                    />
                ) : (
                    <>
                        <RoleSection
                            count={builtinRoles.length}
                            icon={<ShieldCheck size={14} />}
                            label="系统内置"
                            onSelect={onSelect}
                            roles={builtinRoles}
                            selectedName={selectedName}
                        />
                        <RoleSection
                            count={customRoles.length}
                            emptyAction={
                                canCreate ? (
                                    <Button icon={<Plus size={14} />} onClick={onCreate} size="small" type="text">
                                        创建第一个自定义角色
                                    </Button>
                                ) : null
                            }
                            icon={<Shield size={14} />}
                            label="自定义"
                            onSelect={onSelect}
                            roles={customRoles}
                            selectedName={selectedName}
                        />
                    </>
                )}
            </div>
        </ProCard>
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
    const { token } = theme.useToken();

    return (
        <section className="mb-3 last:mb-0">
            <div className="mb-1.5 flex items-center gap-2 px-2 py-1">
                <span
                    className="flex size-6 items-center justify-center rounded-lg"
                    style={{ background: token.colorFillTertiary, color: token.colorTextSecondary }}
                >
                    {icon}
                </span>
                <span className="text-xs font-medium" style={{ color: token.colorTextSecondary }}>
                    {label}
                </span>
                <span
                    className="rounded-full px-1.5 text-[11px] tabular-nums"
                    style={{ background: token.colorFillTertiary, color: token.colorTextTertiary }}
                >
                    {count}
                </span>
            </div>
            {roles.length === 0 ? (
                <div className="px-2 py-2 text-center text-xs" style={{ color: token.colorTextTertiary }}>
                    {emptyAction ?? "暂无角色"}
                </div>
            ) : (
                <div className="space-y-1">
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
    const { token } = theme.useToken();
    const builtin = isBuiltinRole(role);

    return (
        <button
            className="group flex w-full items-start gap-3 rounded-2xl border px-2.5 py-2.5 text-left transition-colors"
            onClick={() => onSelect(role.metadata.name)}
            style={{
                background: selected ? token.colorPrimaryBg : "transparent",
                borderColor: selected ? token.colorPrimaryBorder : "transparent",
            }}
            type="button"
        >
            <span
                className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-xl"
                style={{
                    background: selected ? token.colorPrimary : token.colorFillTertiary,
                    color: selected ? token.colorTextLightSolid : token.colorTextSecondary,
                }}
            >
                {builtin ? <ShieldCheck size={16} /> : <Shield size={16} />}
            </span>
            <span className="min-w-0 flex-1">
                <span className="flex min-w-0 items-center gap-1.5">
                    <span className="truncate text-sm font-medium">{roleDisplayName(role)}</span>
                    {builtin ? <LockKeyhole className="shrink-0 text-neutral-400" size={12} /> : null}
                </span>
                <span
                    className="mt-0.5 block truncate font-mono text-[11px]"
                    style={{ color: token.colorTextTertiary }}
                >
                    {role.metadata.name}
                </span>
                <span className="mt-1 flex items-center justify-between gap-2">
                    <span className="truncate text-xs" style={{ color: token.colorTextSecondary }}>
                        {roleDescription(role) || (builtin ? "平台维护的内置角色" : "尚未填写描述")}
                    </span>
                    {builtin ? (
                        <Tag className="!m-0 !px-1.5 !text-[10px]" color="gold">
                            内置
                        </Tag>
                    ) : (
                        <span className="shrink-0 text-[10px] tabular-nums" style={{ color: token.colorTextTertiary }}>
                            {role.rules?.length ?? 0} 规则
                        </span>
                    )}
                </span>
            </span>
        </button>
    );
}
