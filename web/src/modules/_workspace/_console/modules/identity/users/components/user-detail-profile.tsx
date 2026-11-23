"use client";

import type { User } from "@pudans-dev/api-client";
import { Button, Dropdown, Flex, type MenuProps, Typography, theme } from "antd";
import { KeyRound, Lock, MoreHorizontal, Pencil, Power, Trash2 } from "lucide-react";

import { SectionCard } from "@/components/ui";

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
    const { token } = theme.useToken();
    const displayName = user.spec.displayName || user.metadata.name;
    const loginName = user.spec.loginName || user.metadata.name;
    const actionItems: NonNullable<MenuProps["items"]> = [];

    if (canPassword) {
        actionItems.push({ icon: <KeyRound size={14} />, key: "password", label: "重置密码" });
    }
    if (canStatus) {
        actionItems.push({
            disabled: statusPending,
            icon: user.spec.disabled ? <Power size={14} /> : <Lock size={14} />,
            key: "status",
            label: user.spec.disabled ? "启用账号" : "禁用账号",
        });
    }
    if (canDelete) {
        actionItems.push({ type: "divider" });
        actionItems.push({ danger: true, icon: <Trash2 size={14} />, key: "delete", label: "删除用户" });
    }

    const handleAction: MenuProps["onClick"] = ({ key }) => {
        if (key === "password") {
            onResetPassword();
        } else if (key === "status") {
            onToggleStatus();
        } else if (key === "delete") {
            onDelete();
        }
    };
    const actions =
        actionItems.length > 0 ? (
            <Dropdown menu={{ items: actionItems, onClick: handleAction }} placement="bottomRight" trigger={["click"]}>
                <Button aria-label="更多账号操作" icon={<MoreHorizontal size={16} />} type="text" />
            </Dropdown>
        ) : null;

    return (
        <SectionCard className="!h-auto" title="个人资料">
            <div className="relative">
                {actions ? <div className="absolute top-0 right-0 z-10">{actions}</div> : null}
                <Flex align="center" className="pr-9" gap={18}>
                    <div
                        className="shrink-0 rounded-full border p-1"
                        style={{
                            backgroundColor: token.colorFillQuaternary,
                            borderColor: token.colorBorderSecondary,
                        }}
                    >
                        <UserAvatar name={displayName} size={68} src={user.spec.avatar} />
                    </div>
                    <div className="min-w-0 flex-1">
                        <Typography.Title className="!mb-0" ellipsis level={4}>
                            {displayName}
                        </Typography.Title>
                        <Typography.Text className="mt-0.5 block" ellipsis type="secondary">
                            @{loginName}
                        </Typography.Text>
                        <div className="mt-3 flex flex-wrap gap-2">
                            <UserStatusTag disabled={user.spec.disabled} />
                            <UserGroupTag displayName={groupLabel === "未分组" ? undefined : groupLabel} />
                        </div>
                    </div>
                </Flex>
            </div>
            {canEdit ? (
                <div className="mt-7 border-t pt-6" style={{ borderColor: token.colorBorderSecondary }}>
                    <Button block icon={<Pencil size={15} />} onClick={onEdit} size="large" type="primary">
                        编辑资料
                    </Button>
                </div>
            ) : null}
        </SectionCard>
    );
}
