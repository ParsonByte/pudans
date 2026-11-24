"use client";

import type { ReactNode } from "react";

import type { UserGroup } from "@pudans-dev/api-client";
import {
    DeleteIcon,
    EditIcon,
    LockOnIcon,
    MoreIcon,
    RefreshIcon,
    SecuredIcon,
    UserListIcon,
    UsergroupIcon,
} from "tdesign-icons-react";
import { Button, Dropdown, Tag } from "tdesign-react";
import type { DropdownOption } from "tdesign-react";

import { useDesignTokens } from "@/components/ui";

import {
    isBuiltinUserGroup,
    isSuperAdminsUserGroup,
    userGroupDescription,
    userGroupDisplayName,
} from "../composables/_user-group";

type UserGroupSummaryProps = {
    canDelete: boolean;
    canEdit: boolean;
    group: UserGroup;
    memberCount?: number;
    onDelete: () => void;
    onEdit: () => void;
    onRefresh: () => void;
    roleCount: number;
};

export function UserGroupSummary({
    canDelete,
    canEdit,
    group,
    memberCount,
    onDelete,
    onEdit,
    onRefresh,
    roleCount,
}: UserGroupSummaryProps) {
    const tokens = useDesignTokens();
    const builtin = isBuiltinUserGroup(group);
    const superAdmins = isSuperAdminsUserGroup(group);
    const description = userGroupDescription(group);
    const options: DropdownOption[] = [
        { content: "刷新用户组", prefixIcon: <RefreshIcon />, value: "refresh" },
        ...(canDelete && !builtin
            ? [{ content: "删除用户组", prefixIcon: <DeleteIcon />, theme: "error" as const, value: "delete" }]
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
                        <UsergroupIcon size={19} />
                    </span>
                    <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                            <h2 className="truncate text-lg font-semibold tracking-tight sm:text-xl">
                                {userGroupDisplayName(group)}
                            </h2>
                            <Tag theme={builtin ? "warning" : "primary"} variant="light">
                                {builtin ? "系统内置" : "自定义"}
                            </Tag>
                            {superAdmins ? (
                                <Tag icon={<LockOnIcon />} variant="light">
                                    只读
                                </Tag>
                            ) : null}
                        </div>
                        {description ? (
                            <p className="mt-2 line-clamp-2 max-w-xl text-xs text-[var(--td-text-color-secondary)]">
                                {description}
                            </p>
                        ) : null}
                    </div>
                </div>

                <div className="ms-auto flex shrink-0 flex-wrap items-center gap-2">
                    {canEdit && !builtin ? (
                        <Button icon={<EditIcon />} onClick={onEdit} variant="outline">
                            编辑用户组
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
                <GroupMetric icon={<UserListIcon />} label="成员" value={formatCount(memberCount)} />
                <GroupMetric icon={<SecuredIcon />} label="角色" value={String(roleCount)} />
            </div>
        </div>
    );
}

function GroupMetric({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
    const tokens = useDesignTokens();

    return (
        <span className="inline-flex items-center gap-1.5 text-[11px]">
            <span style={{ color: tokens.colorTextTertiary }}>{icon}</span>
            <span style={{ color: tokens.colorTextTertiary }}>{label}</span>
            <span className="font-medium text-[var(--td-text-color-secondary)] tabular-nums">{value}</span>
        </span>
    );
}

function formatCount(value?: number): string {
    return value === undefined ? "未填写" : String(value);
}
