"use client";

import { useMemo, useState } from "react";

import Link from "next/link";

import type { ListedUser } from "@pudans-dev/api-client";
import { AddIcon, SearchIcon, UserClearIcon } from "tdesign-icons-react";
import { Button, Card, Empty, Input, Skeleton, Table } from "tdesign-react";
import type { TableProps } from "tdesign-react";

import { ScrollArea, confirmGlobalDialog } from "@/components/ui";
import { useMediaQuery } from "@/hooks/ui/use-media-query";

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
    const showEmail = useMediaQuery("(min-width: 1280px)");
    const showPhone = useMediaQuery("(min-width: 1440px)");
    const showRegisteredAt = useMediaQuery("(min-width: 1280px)");
    const showStatusColumn = useMediaQuery("(min-width: 1280px)");
    const userWidth = showPhone ? (canManage ? "22%" : "24%") : showEmail ? (canManage ? "32%" : "36%") : undefined;
    const emailWidth = showPhone ? (canManage ? "20%" : "22%") : canManage ? "26%" : "28%";
    const registeredWidth = showPhone ? "20%" : canManage ? "20%" : "22%";
    const statusWidth = canManage ? "10%" : "12%";
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
            cell: ({ row }) => (
                <MemberCell
                    canViewUsers={canViewUsers}
                    member={row}
                    showInlineStatus={!showStatusColumn}
                    workspace={workspace}
                />
            ),
            colKey: "user",
            title: "用户",
            width: userWidth,
        },
        ...(showEmail
            ? [
                  {
                      cell: ({ row }: { row: MemberRow }) =>
                          row.email ? (
                              <span className="block truncate" title={row.email}>
                                  {row.email}
                              </span>
                          ) : (
                              <EmptyValue />
                          ),
                      colKey: "email",
                      title: "邮箱",
                      width: emailWidth,
                  },
              ]
            : []),
        ...(showPhone
            ? [
                  {
                      cell: ({ row }: { row: MemberRow }) =>
                          row.phone ? (
                              <span className="block truncate" title={row.phone}>
                                  {row.phone}
                              </span>
                          ) : (
                              <EmptyValue />
                          ),
                      colKey: "phone",
                      title: "手机号",
                      width: canManage ? "17%" : "18%",
                  },
              ]
            : []),
        ...(showRegisteredAt
            ? [
                  {
                      cell: ({ row }: { row: MemberRow }) =>
                          row.registeredAt ? formatDateTime(row.registeredAt) : "未填写",
                      colKey: "registeredAt",
                      title: "注册时间",
                      width: registeredWidth,
                  },
              ]
            : []),
        ...(showStatusColumn
            ? [
                  {
                      cell: ({ row }: { row: MemberRow }) => <UserStatusTag disabled={row.disabled} />,
                      colKey: "status",
                      title: "状态",
                      width: statusWidth,
                  },
              ]
            : []),
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
                      width: 88,
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
            header={
                <div className="flex items-baseline gap-2">
                    <span className="text-base font-semibold">成员</span>
                    <span className="text-xs font-normal text-[var(--td-text-color-secondary)]">
                        {loading ? "加载中" : `${rows.length} 位`}
                    </span>
                </div>
            }
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
                    <ScrollArea axis="x" className="min-w-0">
                        <Table<MemberRow>
                            columns={columns}
                            data={rows}
                            pagination={
                                rows.length > 10
                                    ? {
                                          defaultPageSize: 10,
                                          pageSizeOptions: [10, 20, 50],
                                          showPageSize: showStatusColumn,
                                          total: rows.length,
                                      }
                                    : undefined
                            }
                            rowKey="__rowKey"
                            tableLayout="fixed"
                        />
                    </ScrollArea>
                )}
            </div>
        </Card>
    );
}

function MemberCell({
    canViewUsers,
    member,
    showInlineStatus,
    workspace,
}: {
    canViewUsers: boolean;
    member: ListedUser;
    showInlineStatus: boolean;
    workspace: string;
}) {
    const displayName = member.displayName || member.name || "未命名用户";
    const loginName = member.loginName || member.name;
    const content = (
        <>
            <UserAvatar name={displayName} size={34} src={member.avatar} />
            <div className="min-w-0">
                <div className="flex min-w-0 items-center gap-1.5">
                    <span className="truncate text-sm font-medium">{displayName}</span>
                    {showInlineStatus ? <UserStatusTag disabled={member.disabled} /> : null}
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
