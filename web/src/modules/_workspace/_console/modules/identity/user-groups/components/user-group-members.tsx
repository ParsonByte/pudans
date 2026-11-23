"use client";

import { useMemo, useState } from "react";

import Link from "next/link";

import type { ListedUser } from "@pudans-dev/api-client";
import { AddIcon, ArrowRightIcon, SearchIcon, UserClearIcon } from "tdesign-icons-react";
import { Button, Card, Empty, Input, Skeleton, Table } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { confirmGlobalDialog } from "@/components/ui";

import { EmptyValue } from "../../../../components/empty-value";
import { consoleErrorMessage } from "../../../../composables/_errors";
import { formatDateTime } from "../../../../composables/_format";
import { UserAvatar } from "../../users/components/user-avatar";
import { UserStatusTag } from "../../users/components/user-tags";
import { userDetailPath } from "../../users/composables/_user-route";
import { useRemoveUserGroupMember } from "../composables/_user-group-actions";

type UserGroupMembersProps = {
    canManage: boolean;
    canViewUsers: boolean;
    error: unknown;
    groupName: string;
    loading: boolean;
    members?: ListedUser[];
    onAdd: () => void;
    onRetry: () => void;
    workspace: string;
};

type MemberRow = ListedUser & {
    __rowKey: string;
};

export function UserGroupMembers({
    canManage,
    canViewUsers,
    error,
    groupName,
    loading,
    members,
    onAdd,
    onRetry,
    workspace,
}: UserGroupMembersProps) {
    const removeMember = useRemoveUserGroupMember();
    const [keyword, setKeyword] = useState("");
    const rows = useMemo<MemberRow[]>(() => {
        const query = keyword.trim().toLocaleLowerCase();
        const sorted = [...(members ?? [])].sort((left, right) =>
            (left.displayName ?? left.name ?? "").localeCompare(right.displayName ?? right.name ?? "", "zh-Hans-CN"),
        );
        const filtered = query
            ? sorted.filter((member) =>
                  [member.displayName, member.loginName, member.name, member.email, member.phone]
                      .filter(Boolean)
                      .join(" ")
                      .toLocaleLowerCase()
                      .includes(query),
              )
            : sorted;

        return filtered.map((member, index) => ({
            ...member,
            __rowKey: member.name ?? `missing-name-${index}`,
        }));
    }, [keyword, members]);

    const confirmRemove = (member: ListedUser) => {
        if (!member.name) {
            return;
        }

        confirmGlobalDialog({
            cancelText: "取消",
            confirmText: "移出",
            content: `确定将 ${member.displayName || member.name} 移出用户组吗？移出后该用户将变为未分组，相关角色权限会被回收。`,
            danger: true,
            onConfirm: () => {
                void removeMember.mutateAsync({
                    groupName,
                    username: member.name as string,
                });
            },
            title: "移出用户组",
        });
    };

    const columns: NonNullable<TableProps<MemberRow>["columns"]> = [
        {
            cell: ({ row }) => <MemberCell canViewUsers={canViewUsers} member={row} workspace={workspace} />,
            colKey: "user",
            title: "用户",
        },
        {
            cell: ({ row }) => row.email || <EmptyValue />,
            colKey: "email",
            title: "邮箱",
        },
        {
            cell: ({ row }) => row.phone || <EmptyValue />,
            colKey: "phone",
            title: "手机号",
        },
        {
            cell: ({ row }) => (row.registeredAt ? formatDateTime(row.registeredAt) : "未填写"),
            colKey: "registeredAt",
            title: "注册时间",
            width: 170,
        },
        {
            cell: ({ row }) => <UserStatusTag disabled={row.disabled} />,
            colKey: "status",
            title: "状态",
            width: 90,
        },
        ...(canManage
            ? [
                  {
                      cell: ({ row }: { row: MemberRow }) => (
                          <Button
                              disabled={removeMember.isPending}
                              icon={<UserClearIcon />}
                              onClick={() => confirmRemove(row)}
                              size="small"
                              theme="danger"
                              variant="text"
                          >
                              移出
                          </Button>
                      ),
                      colKey: "actions",
                      title: "操作",
                      width: 100,
                  },
              ]
            : []),
    ];

    return (
        <Card
            actions={
                canManage ? (
                    <Button icon={<AddIcon />} onClick={onAdd} size="small" theme="primary">
                        添加成员
                    </Button>
                ) : null
            }
            header={<div className="text-base font-semibold">成员</div>}
            headerBordered
        >
            <div className="flex flex-col gap-4">
                <Input
                    className="w-full sm:max-w-xs"
                    clearable
                    onChange={(value) => setKeyword(String(value))}
                    placeholder="搜索姓名、登录名或联系方式"
                    prefixIcon={<SearchIcon />}
                    value={keyword}
                />
                {error ? (
                    <Empty
                        action={
                            <Button onClick={onRetry} size="small" variant="outline">
                                重试
                            </Button>
                        }
                        description={consoleErrorMessage(error)}
                        title="成员列表加载失败"
                        type="fail"
                    />
                ) : loading ? (
                    <Skeleton animation="gradient" theme="paragraph" />
                ) : rows.length === 0 ? (
                    <Empty
                        action={
                            canManage && !keyword ? (
                                <Button icon={<AddIcon />} onClick={onAdd} theme="primary">
                                    添加第一个成员
                                </Button>
                            ) : undefined
                        }
                        description={keyword ? "没有匹配的成员" : "这个用户组还没有成员"}
                    />
                ) : (
                    <Table<MemberRow>
                        columns={columns}
                        data={rows}
                        pagination={{
                            defaultPageSize: 10,
                            pageSizeOptions: [10, 20, 50],
                            showPageSize: true,
                        }}
                        rowKey="__rowKey"
                    />
                )}
            </div>
        </Card>
    );
}

function MemberCell({
    canViewUsers,
    member,
    workspace,
}: {
    canViewUsers: boolean;
    member: ListedUser;
    workspace: string;
}) {
    const displayName = member.displayName || member.name || "未命名用户";
    const loginName = member.loginName || member.name;
    const content = (
        <>
            <UserAvatar name={displayName} size={34} />
            <div className="min-w-0">
                <div className="flex min-w-0 items-center gap-1.5">
                    <span className="truncate text-sm font-medium">{displayName}</span>
                    {canViewUsers && member.name ? <ArrowRightIcon className="shrink-0" size={12} /> : null}
                </div>
                <div className="truncate font-mono text-[10px] text-[var(--td-text-color-placeholder)]">
                    {loginName ? `@${loginName}` : "未填写"}
                </div>
            </div>
        </>
    );

    if (!canViewUsers || !member.name) {
        return <div className="flex min-w-0 items-center gap-2.5">{content}</div>;
    }

    return (
        <Link className="flex min-w-0 items-center gap-2.5" href={userDetailPath(workspace, member.name)}>
            {content}
        </Link>
    );
}
