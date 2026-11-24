"use client";

import type { ReactNode } from "react";

import type { Role } from "@pudans-dev/api-client";
import {
    BuildingIcon,
    CheckCircleIcon,
    ControlPlatformIcon,
    DeleteIcon,
    EditIcon,
    LockOnIcon,
    MoreIcon,
    RefreshIcon,
    RollbackIcon,
    SecuredIcon,
    ViewListIcon,
} from "tdesign-icons-react";
import { Button, Dropdown, Tag } from "tdesign-react";
import type { DropdownOption } from "tdesign-react";

import { useDesignTokens } from "@/components/ui";

import { isBuiltinRole, roleDisplayName } from "../../composables/_role";
import { RoleIcon } from "./role-icon";

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
    const tokens = useDesignTokens();
    const builtin = isBuiltinRole(role);
    const editable = canEdit && !builtin;
    const options: DropdownOption[] = [
        {
            content: "刷新角色",
            disabled: refreshDisabled,
            prefixIcon: <RefreshIcon />,
            value: "refresh",
        },
        ...(canDelete && !builtin
            ? [{ content: "删除角色", prefixIcon: <DeleteIcon />, theme: "error" as const, value: "delete" }]
            : []),
    ];

    return (
        <div className="flex min-w-0 flex-col gap-4">
            <div className="flex min-w-0 flex-wrap items-start justify-between gap-4">
                <div className="flex min-w-60 flex-1 items-start gap-3">
                    <span
                        className="flex size-10 shrink-0 items-center justify-center rounded-xl"
                        style={{
                            background: builtin ? tokens.colorWarningBg : tokens.colorPrimaryBg,
                            color: builtin ? tokens.colorWarning : tokens.colorPrimary,
                        }}
                    >
                        <RoleIcon role={role} size={19} />
                    </span>
                    <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                            <h2 className="truncate text-lg font-semibold tracking-tight sm:text-xl">
                                {roleDisplayName(role)}
                            </h2>
                            <Tag theme={builtin ? "warning" : "primary"} variant="light">
                                {builtin ? "系统内置" : "自定义"}
                            </Tag>
                            {builtin ? (
                                <Tag icon={<LockOnIcon />} variant="light">
                                    只读
                                </Tag>
                            ) : null}
                            {dirty ? (
                                <Tag theme="warning" variant="light">
                                    未保存
                                </Tag>
                            ) : null}
                        </div>
                    </div>
                </div>

                <div className="ms-auto flex shrink-0 flex-wrap items-center gap-2">
                    {dirty ? (
                        <Button disabled={saving} icon={<RollbackIcon />} onClick={onDiscard} variant="outline">
                            撤销
                        </Button>
                    ) : null}
                    {canSave ? (
                        <Button
                            disabled={!dirty || saving}
                            icon={<CheckCircleIcon />}
                            loading={saving}
                            onClick={onSave}
                            theme="primary"
                        >
                            保存权限
                        </Button>
                    ) : null}
                    {editable ? (
                        <Button icon={<EditIcon />} onClick={onEdit} variant="outline">
                            编辑角色
                        </Button>
                    ) : null}
                    <Dropdown
                        onClick={(item) => {
                            if (item.value === "delete") {
                                onDelete();
                            }
                            if (item.value === "refresh") {
                                onRefresh();
                            }
                        }}
                        options={options}
                        trigger="click"
                    >
                        <Button aria-label="更多操作" icon={<MoreIcon />} shape="square" variant="outline" />
                    </Dropdown>
                </div>
            </div>

            <div className="flex flex-wrap items-center gap-x-4 gap-y-2 md:pl-[52px]">
                <PermissionMetric icon={<ViewListIcon />} label="菜单" value={formatMetric(menuCount, menuTotal)} />
                <PermissionMetric
                    icon={<ControlPlatformIcon />}
                    label="操作"
                    value={formatMetric(operationCount, operationTotal)}
                />
                <PermissionMetric icon={<BuildingIcon />} label="数据规则" value={String(ruleCount)} />
                <PermissionMetric
                    icon={<SecuredIcon />}
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
    const tokens = useDesignTokens();

    return (
        <span className="inline-flex items-center gap-1.5 text-[11px]">
            <span style={{ color: tokens.colorTextTertiary }}>{icon}</span>
            <span style={{ color: tokens.colorTextTertiary }}>{label}</span>
            <span className="font-medium text-[var(--td-text-color-secondary)] tabular-nums">{value}</span>
        </span>
    );
}

function formatMetric(value?: number, total?: number): string {
    if (value === undefined) {
        return "未填写";
    }
    if (total === undefined || total === 0) {
        return String(value);
    }
    return `${value}/${total}`;
}
