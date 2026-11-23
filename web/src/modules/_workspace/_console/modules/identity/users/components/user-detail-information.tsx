"use client";

import type { User } from "@pudans-dev/api-client";
import { Tag, Typography } from "tdesign-react";

import { SectionCard } from "@/components/ui";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { DescriptionGrid } from "../../../../components/description-grid";
import { EmptyValue } from "../../../../components/empty-value";
import { formatDateTime } from "../../../../composables/_format";
import { UserGroupTag, UserStatusTag } from "./user-tags";

type UserDetailInformationProps = {
    groupLabel: string;
    user: User;
};

export function UserDetailInformation({ groupLabel, user }: UserDetailInformationProps) {
    return (
        <SectionCard className="!h-auto" title="账号资料">
            <DescriptionGrid
                columns={2}
                items={[
                    {
                        label: "登录名",
                        value: <Typography.Text code>@{user.spec.loginName || user.metadata.name}</Typography.Text>,
                    },
                    {
                        label: "用户组",
                        value: <UserGroupTag displayName={groupLabel === "未分组" ? undefined : groupLabel} />,
                    },
                    { label: "账号状态", value: <UserStatusTag disabled={user.spec.disabled} /> },
                    { label: "邮箱", value: user.spec.email || <EmptyValue /> },
                    { label: "手机号", value: user.spec.phone || <EmptyValue /> },
                    {
                        label: "创建时间",
                        value: user.spec.registeredAt ? formatDateTime(user.spec.registeredAt) : "未填写",
                    },
                    {
                        label: "邮箱验证",
                        value: (
                            <Tag theme={user.spec.emailVerified ? "success" : "default"} variant="light">
                                {user.spec.emailVerified ? "已验证" : "未验证"}
                            </Tag>
                        ),
                    },
                    {
                        label: "双因素认证",
                        value: (
                            <Tag theme={user.spec.twoFactorAuthEnabled ? "success" : "default"} variant="light">
                                {user.spec.twoFactorAuthEnabled ? "已启用" : "未启用"}
                            </Tag>
                        ),
                    },
                    {
                        label: "用户编号",
                        value: <CopyableIdentifier className="max-w-[360px]" value={user.spec.uid} />,
                    },
                    {
                        label: "个人简介",
                        value: user.spec.bio || <EmptyValue />,
                    },
                ]}
            />
        </SectionCard>
    );
}
