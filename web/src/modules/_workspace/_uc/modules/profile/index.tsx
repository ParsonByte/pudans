"use client";

import { useState } from "react";

import { EditIcon, MailIcon, MobileIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Card, Empty, Skeleton, Tag, Typography } from "tdesign-react";

import { SectionCard } from "@/components/ui";
import { CopyableIdentifier } from "@/modules/_workspace/_console/components/copyable-identifier";
import { DescriptionGrid } from "@/modules/_workspace/_console/components/description-grid";
import { EmptyValue } from "@/modules/_workspace/_console/components/empty-value";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { formatDateTime } from "@/modules/_workspace/_console/composables/_format";
import { UserAvatar } from "@/modules/_workspace/_console/modules/identity/users/components/user-avatar";

import { useSelfPermissionsQuery, useSelfProfileQuery } from "../../composables/_queries";
import { ProfileEditorDialog } from "./components/profile-editor-dialog";

export function ProfileView() {
    const [editing, setEditing] = useState(false);
    const profileQuery = useSelfProfileQuery();
    const permissionsQuery = useSelfPermissionsQuery();
    const user = profileQuery.data;

    if (profileQuery.isPending) {
        return (
            <div className="flex flex-col gap-4">
                <Skeleton theme="avatar-text" />
                <Skeleton theme="paragraph" />
            </div>
        );
    }

    if (profileQuery.error || !user) {
        return (
            <Empty
                action={
                    <Button icon={<RefreshIcon />} onClick={() => void profileQuery.refetch()}>
                        重试
                    </Button>
                }
                description={profileQuery.error ? consoleErrorMessage(profileQuery.error) : "当前账号资料不可用。"}
                title="个人资料加载失败"
                type="fail"
            />
        );
    }

    const displayName = user.spec?.displayName || user.metadata?.name || "当前用户";
    const loginName = user.spec?.loginName || user.metadata?.name;
    const roles = permissionsQuery.data?.roles ?? [];

    return (
        <>
            <div className="flex min-w-0 flex-col gap-4">
                <Card bordered={false} shadow={false}>
                    <div className="flex flex-col gap-5 md:flex-row md:items-center md:justify-between">
                        <div className="flex min-w-0 items-center gap-4">
                            <UserAvatar name={displayName} size={72} src={user.spec?.avatar} />
                            <div className="min-w-0">
                                <div className="flex flex-wrap items-center gap-2">
                                    <Typography.Title className="!mb-0" level="h4">
                                        {displayName}
                                    </Typography.Title>
                                    <Tag theme={user.spec?.disabled ? "default" : "success"} variant="light">
                                        {user.spec?.disabled ? "已禁用" : "正常"}
                                    </Tag>
                                </div>
                                <Typography.Text className="mt-1 block font-mono text-xs" theme="secondary">
                                    @{loginName}
                                </Typography.Text>
                                <div className="mt-3 flex flex-wrap gap-x-5 gap-y-2 text-sm">
                                    <span className="flex min-w-0 items-center gap-1.5">
                                        <MailIcon className="shrink-0 text-[var(--td-text-color-placeholder)]" />
                                        <Typography.Text className="truncate" theme="secondary">
                                            {user.spec?.email || "未填写邮箱"}
                                        </Typography.Text>
                                    </span>
                                    <span className="flex items-center gap-1.5">
                                        <MobileIcon className="text-[var(--td-text-color-placeholder)]" />
                                        <Typography.Text theme="secondary">
                                            {user.spec?.phone || "未填写手机号"}
                                        </Typography.Text>
                                    </span>
                                </div>
                            </div>
                        </div>
                        <Tag theme="primary" variant="light">
                            飞书同步
                        </Tag>
                    </div>
                </Card>

                <div className="grid min-w-0 gap-4 lg:grid-cols-[minmax(0,320px)_minmax(0,1fr)]">
                    <SectionCard title="账号信息">
                        <DescriptionGrid
                            columns={2}
                            items={[
                                {
                                    label: "用户编号",
                                    value: user.spec?.uid ? (
                                        <CopyableIdentifier
                                            display={
                                                <span className="font-mono text-xs break-all">{user.spec.uid}</span>
                                            }
                                            quiet
                                            value={user.spec.uid}
                                        />
                                    ) : (
                                        <EmptyValue />
                                    ),
                                },
                                {
                                    label: "注册时间",
                                    value: user.spec?.registeredAt ? formatDateTime(user.spec.registeredAt) : "未填写",
                                },
                                {
                                    label: "邮箱验证",
                                    value: (
                                        <Tag theme={user.spec?.emailVerified ? "success" : "default"} variant="light">
                                            {user.spec?.emailVerified ? "已验证" : "未验证"}
                                        </Tag>
                                    ),
                                },
                                {
                                    label: "双重验证",
                                    value: (
                                        <Tag
                                            theme={user.spec?.twoFactorAuthEnabled ? "success" : "default"}
                                            variant="light"
                                        >
                                            {user.spec?.twoFactorAuthEnabled ? "已启用" : "未启用"}
                                        </Tag>
                                    ),
                                },
                            ]}
                        />
                    </SectionCard>

                    <div className="flex min-w-0 flex-col gap-4">
                        <SectionCard
                            extra={
                                <Button
                                    icon={<EditIcon />}
                                    onClick={() => setEditing(true)}
                                    size="small"
                                    variant="text"
                                >
                                    编辑
                                </Button>
                            }
                            title="个人资料"
                        >
                            <DescriptionGrid
                                columns={2}
                                items={[
                                    { label: "显示名称", value: displayName },
                                    { label: "登录名", value: <Typography.Text code>@{loginName}</Typography.Text> },
                                    { label: "邮箱", value: user.spec?.email || <EmptyValue /> },
                                    { label: "手机", value: user.spec?.phone || <EmptyValue /> },
                                ]}
                            />
                            <div className="mt-5 border-t border-[var(--td-component-border)] pt-4">
                                <div className="text-sm text-[var(--td-text-color-placeholder)]">个人简介</div>
                                <Typography.Paragraph className="!mt-1 !mb-0 whitespace-pre-wrap">
                                    {user.spec?.bio || <EmptyValue />}
                                </Typography.Paragraph>
                            </div>
                        </SectionCard>

                        <SectionCard
                            extra={
                                <span className="text-xs text-[var(--td-text-color-secondary)]">
                                    {permissionsQuery.isPending ? "加载中" : `${roles.length} 个角色`}
                                </span>
                            }
                            title="角色"
                        >
                            {permissionsQuery.error ? (
                                <Empty description={consoleErrorMessage(permissionsQuery.error)} title="角色加载失败" />
                            ) : null}
                            {permissionsQuery.isPending ? <Skeleton theme="paragraph" /> : null}
                            {permissionsQuery.isSuccess ? (
                                roles.length > 0 ? (
                                    <div className="flex flex-wrap gap-2">
                                        {roles.map((role) => (
                                            <Tag key={role} theme="primary" variant="light">
                                                {role}
                                            </Tag>
                                        ))}
                                    </div>
                                ) : (
                                    <Empty description="当前账号尚未分配角色" />
                                )
                            ) : null}
                        </SectionCard>
                    </div>
                </div>
            </div>

            {editing ? <ProfileEditorDialog onClose={() => setEditing(false)} user={user} /> : null}
        </>
    );
}
