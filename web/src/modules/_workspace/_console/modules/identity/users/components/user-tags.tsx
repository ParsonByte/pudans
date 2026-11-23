"use client";

import { Tag, Typography } from "tdesign-react";

type UserStatusTagProps = {
    disabled?: boolean;
};

type UserGroupTagProps = {
    displayName?: string;
    name?: string;
};

export function UserStatusTag({ disabled }: UserStatusTagProps) {
    return (
        <Tag theme={disabled ? "default" : "success"} variant="light">
            {disabled ? "已禁用" : "正常"}
        </Tag>
    );
}

export function UserGroupTag({ displayName, name }: UserGroupTagProps) {
    const groupName = name?.trim();

    if (!displayName && !groupName) {
        return <Typography.Text theme="secondary">未分组</Typography.Text>;
    }

    return (
        <Tag theme="primary" title={displayName ? undefined : groupName} variant="light">
            {displayName ?? "未知用户组"}
        </Tag>
    );
}
