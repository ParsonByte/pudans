"use client";

import { Tag, Typography } from "antd";

type UserStatusTagProps = {
    disabled?: boolean;
};

type UserGroupTagProps = {
    displayName?: string;
    name?: string;
};

export function UserStatusTag({ disabled }: UserStatusTagProps) {
    return <Tag color={disabled ? "default" : "success"}>{disabled ? "已禁用" : "正常"}</Tag>;
}

export function UserGroupTag({ displayName, name }: UserGroupTagProps) {
    const groupName = name?.trim();

    if (!displayName && !groupName) {
        return <Typography.Text type="secondary">未分组</Typography.Text>;
    }

    return (
        <Tag color="processing" title={displayName ? undefined : groupName}>
            {displayName ?? "未知用户组"}
        </Tag>
    );
}
