"use client";

import Link from "next/link";

import type { ListedUser } from "@pudans-dev/api-client";
import { ArrowRightIcon, EditIcon } from "tdesign-icons-react";
import { Button, Space } from "tdesign-react";

import { useDesignTokens } from "@/components/ui";

import { DescriptionGrid } from "../../../../components/description-grid";
import { EmptyValue } from "../../../../components/empty-value";
import { formatDateTime } from "../../../../composables/_format";
import { userDetailPath } from "../composables/_user-route";
import { UserGroupTag, UserStatusTag } from "./user-tags";

type UserExpandedRowProps = {
    canEdit: boolean;
    onEdit: () => void;
    user: ListedUser;
    workspace: string;
};

export function UserExpandedRow({ canEdit, onEdit, user, workspace }: UserExpandedRowProps) {
    const tokens = useDesignTokens();
    const displayName = user.displayName || user.name;
    const loginName = user.loginName || user.name;

    return (
        <div className="w-full rounded-2xl px-4 py-4" style={{ backgroundColor: tokens.colorFillQuaternary }}>
            <DescriptionGrid
                columns={4}
                items={[
                    { label: "显示名称", value: displayName || <EmptyValue /> },
                    {
                        label: "登录名",
                        value: loginName ? <span className="font-mono text-xs">@{loginName}</span> : <EmptyValue />,
                    },
                    {
                        label: "用户组",
                        value: <UserGroupTag displayName={user.userGroupDisplayName} name={user.userGroup} />,
                    },
                    { label: "状态", value: <UserStatusTag disabled={user.disabled} /> },
                    {
                        label: "邮箱",
                        value: user.email ? <span title={user.email}>{user.email}</span> : <EmptyValue />,
                    },
                    {
                        label: "手机号",
                        value: user.phone ? <span title={user.phone}>{user.phone}</span> : <EmptyValue />,
                    },
                    { label: "创建时间", value: formatDateTime(user.registeredAt) },
                ]}
            />
            {user.name || canEdit ? (
                <div
                    className="mt-4 flex justify-end border-t pt-2"
                    style={{ borderColor: tokens.colorBorderSecondary }}
                >
                    <Space size={4}>
                        {user.name ? (
                            <Link href={userDetailPath(workspace, user.name)}>
                                <Button icon={<ArrowRightIcon />} size="small" variant="text">
                                    查看详情
                                </Button>
                            </Link>
                        ) : null}
                        {canEdit ? (
                            <Button icon={<EditIcon />} onClick={onEdit} size="small" variant="text">
                                编辑资料
                            </Button>
                        ) : null}
                    </Space>
                </div>
            ) : null}
        </div>
    );
}
