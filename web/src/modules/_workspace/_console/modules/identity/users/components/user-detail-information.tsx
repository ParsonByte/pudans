"use client";

import { ProDescriptions } from "@ant-design/pro-components";
import type { User } from "@pudans-dev/api-client";
import { Tag, Typography, theme } from "antd";

import { SectionCard } from "@/components/ui";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { EmptyValue } from "../../../../components/empty-value";
import { formatDateTime } from "../../../../composables/_format";
import { UserGroupTag, UserStatusTag } from "./user-tags";

type UserDetailInformationProps = {
    groupLabel: string;
    user: User;
};

type UserInformation = {
    bio?: string;
    email?: string;
    emailVerified: boolean;
    groupLabel: string;
    loginName: string;
    phone?: string;
    registeredAt?: string;
    twoFactorAuthEnabled: boolean;
    uid?: string;
};

export function UserDetailInformation({ groupLabel, user }: UserDetailInformationProps) {
    const { token } = theme.useToken();
    const information: UserInformation = {
        bio: user.spec.bio,
        email: user.spec.email,
        emailVerified: Boolean(user.spec.emailVerified),
        groupLabel,
        loginName: user.spec.loginName || user.metadata.name,
        phone: user.spec.phone,
        registeredAt: user.spec.registeredAt,
        twoFactorAuthEnabled: Boolean(user.spec.twoFactorAuthEnabled),
        uid: user.spec.uid,
    };

    return (
        <SectionCard className="!h-auto" title="账号资料">
            <ProDescriptions<UserInformation>
                column={{ xs: 1, sm: 1, md: 1, lg: 1, xl: 2, xxl: 2 }}
                columns={[
                    {
                        dataIndex: "loginName",
                        render: (_, item) => (
                            <Typography.Text className="font-mono text-xs">@{item.loginName}</Typography.Text>
                        ),
                        title: "登录名",
                    },
                    {
                        dataIndex: "groupLabel",
                        render: (_, item) => (
                            <UserGroupTag displayName={item.groupLabel === "未分组" ? undefined : item.groupLabel} />
                        ),
                        title: "用户组",
                    },
                    {
                        render: () => <UserStatusTag disabled={user.spec.disabled} />,
                        title: "账号状态",
                    },
                    {
                        dataIndex: "email",
                        render: (_, item) => item.email || <EmptyValue />,
                        title: "邮箱",
                    },
                    {
                        dataIndex: "phone",
                        render: (_, item) => item.phone || <EmptyValue />,
                        title: "手机号",
                    },
                    {
                        dataIndex: "registeredAt",
                        render: (_, item) => formatDateTime(item.registeredAt),
                        title: "创建时间",
                    },
                    {
                        dataIndex: "emailVerified",
                        render: (_, item) => (
                            <Tag color={item.emailVerified ? "success" : "default"}>
                                {item.emailVerified ? "已验证" : "未验证"}
                            </Tag>
                        ),
                        title: "邮箱验证",
                    },
                    {
                        dataIndex: "twoFactorAuthEnabled",
                        render: (_, item) => (
                            <Tag color={item.twoFactorAuthEnabled ? "success" : "default"}>
                                {item.twoFactorAuthEnabled ? "已启用" : "未启用"}
                            </Tag>
                        ),
                        title: "双因素认证",
                    },
                    {
                        dataIndex: "uid",
                        render: (_, item) => <CopyableIdentifier className="max-w-[360px]" value={item.uid} />,
                        title: "用户编号",
                    },
                    {
                        dataIndex: "bio",
                        render: (_, item) => item.bio || <EmptyValue />,
                        title: "个人简介",
                    },
                ]}
                dataSource={information}
                size="small"
                styles={{ label: { color: token.colorTextTertiary, width: 88 } }}
            />
        </SectionCard>
    );
}
