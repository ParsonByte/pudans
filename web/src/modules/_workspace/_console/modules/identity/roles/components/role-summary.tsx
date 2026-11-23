"use client";

import type { ReactNode } from "react";

import type { Role } from "@pudans-dev/api-client";
import { Button, Dropdown, Tag, Tooltip, theme } from "antd";
import {
    Building2,
    ListChecks,
    LockKeyhole,
    Menu,
    MoreHorizontal,
    MousePointerClick,
    Pencil,
    RefreshCw,
    RotateCcw,
    Save,
    Shield,
    ShieldCheck,
    Trash2,
} from "lucide-react";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { formatDateTime } from "../../../../composables/_format";
import { isBuiltinRole, roleDescription, roleDisplayName } from "../../composables/_role";

type RoleSummaryProps = {
    canDelete: boolean;
    canEdit: boolean;
    canSave: boolean;
    dirty: boolean;
    menuCount?: number;
    menuTotal?: number;
    onDelete: () => void;
    onDiscard: () => void;
    onEdit: () => void;
    onRefresh: () => void;
    onSave: () => void;
    operationCount?: number;
    operationTotal?: number;
    refreshDisabled: boolean;
    role: Role;
    ruleCount: number;
    saving: boolean;
    workspaceCount: number;
    workspaceTotal: number;
};

export function RoleSummary({
    canDelete,
    canEdit,
    canSave,
    dirty,
    menuCount,
    menuTotal,
    onDelete,
    onDiscard,
    onEdit,
    onRefresh,
    onSave,
    operationCount,
    operationTotal,
    refreshDisabled,
    role,
    ruleCount,
    saving,
    workspaceCount,
    workspaceTotal,
}: RoleSummaryProps) {
    const { token } = theme.useToken();
    const builtin = isBuiltinRole(role);
    const editable = canEdit && !builtin;
    const moreItems = [
        {
            disabled: refreshDisabled,
            icon: <RefreshCw size={14} />,
            key: "refresh",
            label: refreshDisabled ? "刷新角色（请先保存或撤销）" : "刷新角色",
        },
        ...(canDelete && !builtin
            ? [{ danger: true, icon: <Trash2 size={14} />, key: "delete", label: "删除角色" }]
            : []),
    ];

    return (
        <div className="flex min-w-0 flex-col gap-4">
            <div className="flex min-w-0 flex-wrap items-start justify-between gap-4">
                <div className="flex min-w-[240px] flex-1 items-start gap-3">
                    <span
                        className="flex size-10 shrink-0 items-center justify-center rounded-xl"
                        style={{
                            background: builtin ? token.colorWarningBg : token.colorPrimaryBg,
                            color: builtin ? token.colorWarning : token.colorPrimary,
                        }}
                    >
                        {builtin ? <ShieldCheck size={19} /> : <Shield size={19} />}
                    </span>
                    <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                            <h2 className="truncate text-lg font-semibold tracking-tight sm:text-xl">
                                {roleDisplayName(role)}
                            </h2>
                            <Tag className="!mr-0" color={builtin ? "gold" : "blue"}>
                                {builtin ? "系统内置" : "自定义"}
                            </Tag>
                            {builtin ? (
                                <Tag className="!mr-0" icon={<LockKeyhole size={11} />}>
                                    只读
                                </Tag>
                            ) : null}
                            {dirty ? (
                                <Tag className="!mr-0" color="warning">
                                    未保存
                                </Tag>
                            ) : null}
                        </div>
                        <div
                            className="mt-1 max-w-2xl text-xs leading-5 sm:text-sm"
                            style={{ color: token.colorTextSecondary }}
                        >
                            {roleDescription(role) ||
                                (builtin
                                    ? "由平台维护的系统角色，权限与授权关系不可在此修改。"
                                    : "尚未填写角色描述，可在编辑角色中补充。")}
                        </div>
                        <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1">
                            <div className="max-w-[280px] min-w-0">
                                <CopyableIdentifier value={role.metadata.name} />
                            </div>
                            <span className="text-[11px]" style={{ color: token.colorTextTertiary }}>
                                创建于 {formatDateTime(role.metadata.creationTimestamp)}
                            </span>
                        </div>
                    </div>
                </div>

                <div className="ms-auto flex shrink-0 flex-wrap items-center gap-2">
                    {dirty ? (
                        <Button disabled={saving} icon={<RotateCcw size={14} />} onClick={onDiscard}>
                            撤销
                        </Button>
                    ) : null}
                    {canSave ? (
                        <Tooltip title={!dirty ? "当前没有未保存的权限变更" : undefined}>
                            <span>
                                <Button
                                    disabled={!dirty || saving}
                                    icon={<Save size={14} />}
                                    loading={saving}
                                    onClick={onSave}
                                    type="primary"
                                >
                                    保存权限
                                </Button>
                            </span>
                        </Tooltip>
                    ) : null}
                    {editable ? (
                        <Button icon={<Pencil size={14} />} onClick={onEdit}>
                            编辑角色
                        </Button>
                    ) : null}
                    <Dropdown
                        menu={{
                            items: moreItems,
                            onClick: ({ key }) => {
                                if (key === "delete") {
                                    onDelete();
                                }
                                if (key === "refresh") {
                                    onRefresh();
                                }
                            },
                        }}
                        trigger={["click"]}
                    >
                        <Tooltip title="更多操作">
                            <Button aria-label="更多操作" icon={<MoreHorizontal size={16} />} />
                        </Tooltip>
                    </Dropdown>
                </div>
            </div>

            <div className="flex flex-wrap items-center gap-x-4 gap-y-2 md:pl-[52px]">
                <PermissionMetric icon={<Menu size={12} />} label="菜单" value={formatMetric(menuCount, menuTotal)} />
                <PermissionMetric
                    icon={<MousePointerClick size={12} />}
                    label="操作"
                    value={formatMetric(operationCount, operationTotal)}
                />
                <PermissionMetric icon={<ListChecks size={12} />} label="数据规则" value={String(ruleCount)} />
                <PermissionMetric
                    icon={<Building2 size={12} />}
                    label="工作区"
                    value={formatMetric(workspaceCount, workspaceTotal)}
                />
            </div>
        </div>
    );
}

type PermissionMetricProps = {
    icon: ReactNode;
    label: string;
    value: string;
};

function PermissionMetric({ icon, label, value }: PermissionMetricProps) {
    const { token } = theme.useToken();

    return (
        <span className="inline-flex items-center gap-1.5 text-[11px]">
            <span style={{ color: token.colorTextTertiary }}>{icon}</span>
            <span style={{ color: token.colorTextTertiary }}>{label}</span>
            <span className="font-medium tabular-nums" style={{ color: token.colorTextSecondary }}>
                {value}
            </span>
        </span>
    );
}

function formatMetric(value?: number, total?: number): string {
    if (value === undefined) {
        return "—";
    }
    if (total === undefined || total === 0) {
        return String(value);
    }
    return `${value}/${total}`;
}
