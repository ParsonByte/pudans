"use client";

import { Space, Tag, Tooltip } from "tdesign-react";

import { formatCount } from "../../../../composables/_format";

type PatRoleTagsProps = {
    limit?: number;
    roles?: string[];
};

// 角色留空表示继承当前用户的角色。
export function PatRoleTags({ limit, roles }: PatRoleTagsProps) {
    const values = roles ?? [];
    if (values.length === 0) {
        return (
            <Tag theme="default" variant="light">
                继承用户角色
            </Tag>
        );
    }

    const visible = limit === undefined ? values : values.slice(0, limit);
    const hidden = values.length - visible.length;

    return (
        <Space breakLine size={[4, 4]}>
            {visible.map((role) => (
                <Tooltip content={role} key={role}>
                    <Tag className="max-w-40 truncate font-mono" theme="primary" variant="light">
                        {role}
                    </Tag>
                </Tooltip>
            ))}
            {hidden > 0 ? <Tag>+{formatCount(hidden)}</Tag> : null}
        </Space>
    );
}
