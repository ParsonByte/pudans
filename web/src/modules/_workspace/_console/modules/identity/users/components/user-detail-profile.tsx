"use client";

import type { User } from "@pudans-dev/api-client";
import { DeleteIcon, KeyIcon, MoreIcon, PlayCircleIcon, PoweroffIcon } from "tdesign-icons-react";
import { Button, Card, Dropdown, type DropdownOption, Typography } from "tdesign-react";

import { UserAvatar } from "./user-avatar";

type UserDetailProfileProps = {
    canDelete: boolean;
    canPassword: boolean;
    canStatus: boolean;
    groupLabel: string;
    onDelete: () => void;
    onResetPassword: () => void;
    onToggleStatus: () => void;
    statusPending: boolean;
    user: User;
};

export function UserDetailProfile({
    canDelete,
    canPassword,
    canStatus,
    groupLabel,
    onDelete,
    onResetPassword,
    onToggleStatus,
    statusPending,
    user,
}: UserDetailProfileProps) {
    const displayName = user.spec.displayName || user.metadata.name;
    const loginName = user.spec.loginName || user.metadata.name;
    const actionItems: DropdownOption[] = [];

    if (canPassword) {
        actionItems.push({ content: "重置密码", prefixIcon: <KeyIcon />, value: "password" });
    }
    if (canStatus) {
        actionItems.push({
            content: user.spec.disabled ? "启用用户" : "禁用用户",
            disabled: statusPending,
            prefixIcon: user.spec.disabled ? <PlayCircleIcon /> : <PoweroffIcon />,
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
                <Button
                    aria-label="更多账号操作"
                    className="!border-transparent !bg-transparent !text-white hover:!bg-white/10 focus:!bg-white/10 focus-visible:!bg-white/10 active:!bg-white/10"
                    icon={<MoreIcon />}
                    shape="square"
                    variant="text"
                />
            </Dropdown>
        ) : null;

    return (
        <Card
            bordered={false}
            bodyClassName="!p-6"
            className="overflow-hidden"
            shadow={false}
            style={{ background: "var(--td-brand-color)" }}
        >
            <div className="flex items-start justify-between gap-3">
                <UserAvatar name={displayName} size={90} src={user.spec.avatar} />
                {actions}
            </div>
            <Typography.Title className="!mt-9" ellipsis level="h4" style={{ color: "var(--td-text-color-anti)" }}>
                {displayName}
            </Typography.Title>
            <div className="mt-2 text-sm" style={{ color: "var(--td-text-color-anti)" }}>
                @{loginName}
            </div>
            <div className="mt-1 text-sm opacity-80" style={{ color: "var(--td-text-color-anti)" }}>
                {groupLabel}
            </div>
        </Card>
    );
}
