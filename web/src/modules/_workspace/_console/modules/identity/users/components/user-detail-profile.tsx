"use client";

import type { User } from "@pudans-dev/api-client";
import { DeleteIcon, EditIcon, KeyIcon, LockOffIcon, LockOnIcon, MoreIcon } from "tdesign-icons-react";
import { Button, Dropdown, type DropdownOption, Typography } from "tdesign-react";

import { SectionCard, useDesignTokens } from "@/components/ui";

import { UserAvatar } from "./user-avatar";
import { UserGroupTag, UserStatusTag } from "./user-tags";

type UserDetailProfileProps = {
    canDelete: boolean;
    canEdit: boolean;
    canPassword: boolean;
    canStatus: boolean;
    groupLabel: string;
    onDelete: () => void;
    onEdit: () => void;
    onResetPassword: () => void;
    onToggleStatus: () => void;
    statusPending: boolean;
    user: User;
};

export function UserDetailProfile({
    canDelete,
    canEdit,
    canPassword,
    canStatus,
    groupLabel,
    onDelete,
    onEdit,
    onResetPassword,
    onToggleStatus,
    statusPending,
    user,
}: UserDetailProfileProps) {
    const tokens = useDesignTokens();
    const displayName = user.spec.displayName || user.metadata.name;
    const loginName = user.spec.loginName || user.metadata.name;
    const actionItems: DropdownOption[] = [];

    if (canPassword) {
        actionItems.push({ content: "重置密码", prefixIcon: <KeyIcon />, value: "password" });
    }
    if (canStatus) {
        actionItems.push({
            content: user.spec.disabled ? "启用账号" : "禁用账号",
            disabled: statusPending,
            prefixIcon: user.spec.disabled ? <LockOnIcon /> : <LockOffIcon />,
            value: "status",
        });
    }
    if (canDelete) {
        actionItems.push({ divider: true, value: "divider" });
        actionItems.push({ content: "删除用户", prefixIcon: <DeleteIcon />, theme: "error", value: "delete" });
    }

    const handleAction = (item: DropdownOption) => {
        if (item.value === "password") {
            onResetPassword();
        } else if (item.value === "status") {
            onToggleStatus();
        } else if (item.value === "delete") {
            onDelete();
        }
    };
    const actions =
        actionItems.length > 0 ? (
            <Dropdown onClick={handleAction} options={actionItems} placement="bottom-right" trigger="click">
                <Button aria-label="更多账号操作" icon={<MoreIcon />} shape="square" variant="text" />
            </Dropdown>
        ) : null;

    return (
        <SectionCard className="!h-auto" extra={actions} title="个人资料">
            <div className="relative">
                <div className="flex items-center gap-[18px]">
                    <div
                        className="shrink-0 rounded-full border p-1"
                        style={{
                            backgroundColor: tokens.colorFillQuaternary,
                            borderColor: tokens.colorBorderSecondary,
                        }}
                    >
                        <UserAvatar name={displayName} size={68} src={user.spec.avatar} />
                    </div>
                    <div className="min-w-0 flex-1">
                        <Typography.Title ellipsis level="h4">
                            {displayName}
                        </Typography.Title>
                        <Typography.Text className="mt-0.5 block" ellipsis theme="secondary">
                            @{loginName}
                        </Typography.Text>
                        <div className="mt-3 flex flex-wrap gap-2">
                            <UserStatusTag disabled={user.spec.disabled} />
                            <UserGroupTag displayName={groupLabel === "未分组" ? undefined : groupLabel} />
                        </div>
                    </div>
                </div>
            </div>
            {canEdit ? (
                <div className="mt-7 border-t pt-6" style={{ borderColor: tokens.colorBorderSecondary }}>
                    <Button block icon={<EditIcon />} onClick={onEdit} size="large" theme="primary">
                        编辑资料
                    </Button>
                </div>
            ) : null}
        </SectionCard>
    );
}
