"use client";

import type { User } from "@pudans-dev/api-client";
import { EditIcon } from "tdesign-icons-react";
import { Button, Card, Col, Row, Tag, Typography } from "tdesign-react";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { EmptyValue } from "../../../../components/empty-value";
import { formatDateTime } from "../../../../composables/_format";
import { UserGroupTag, UserStatusTag } from "./user-tags";

type UserDetailInformationProps = {
    canEdit: boolean;
    groupLabel: string;
    onEdit: () => void;
    user: User;
};

export function UserDetailInformation({ canEdit, groupLabel, onEdit, user }: UserDetailInformationProps) {
    return (
        <Card
            actions={
                canEdit ? (
                    <Button
                        aria-label="编辑个人信息"
                        icon={<EditIcon />}
                        onClick={onEdit}
                        shape="square"
                        variant="text"
                    />
                ) : null
            }
            bordered={false}
            shadow={false}
            title="个人信息"
        >
            <Row gutter={[16, 16]}>
                <InformationItem label="手机">{user.spec.phone || <EmptyValue />}</InformationItem>
                <InformationItem label="邮箱">{user.spec.email || <EmptyValue />}</InformationItem>
                <InformationItem label="用户组">
                    <UserGroupTag displayName={groupLabel === "未分组" ? undefined : groupLabel} />
                </InformationItem>
                <InformationItem label="账号状态">
                    <UserStatusTag disabled={user.spec.disabled} />
                </InformationItem>
                <InformationItem label="登录名">
                    <Typography.Text code>@{user.spec.loginName || user.metadata.name}</Typography.Text>
                </InformationItem>
                <InformationItem label="用户编号" span={6}>
                    <CopyableIdentifier
                        className="max-w-full"
                        display={<span className="font-mono text-xs break-all">{user.spec.uid}</span>}
                        value={user.spec.uid}
                    />
                </InformationItem>
                <InformationItem label="创建时间">
                    {user.spec.registeredAt ? formatDateTime(user.spec.registeredAt) : "未填写"}
                </InformationItem>
                <InformationItem label="邮箱验证">
                    <Tag theme={user.spec.emailVerified ? "success" : "default"} variant="light">
                        {user.spec.emailVerified ? "已验证" : "未验证"}
                    </Tag>
                </InformationItem>
                <InformationItem label="双因素认证">
                    <Tag theme={user.spec.twoFactorAuthEnabled ? "success" : "default"} variant="light">
                        {user.spec.twoFactorAuthEnabled ? "已启用" : "未启用"}
                    </Tag>
                </InformationItem>
                <InformationItem label="个人简介" span={12}>
                    {user.spec.bio || <EmptyValue />}
                </InformationItem>
            </Row>
        </Card>
    );
}

type InformationItemProps = {
    children: React.ReactNode;
    label: string;
    span?: number;
};

function InformationItem({ children, label, span = 3 }: InformationItemProps) {
    return (
        <Col span={span} xs={12} sm={span === 12 ? 12 : 6} xl={span}>
            <div className="text-sm text-[var(--td-text-color-placeholder)]">{label}</div>
            <div className="mt-1 min-w-0 text-sm text-[var(--td-text-color-primary)]">{children}</div>
        </Col>
    );
}
