"use client";

import Link from "next/link";

import { Button, Card, Empty, Skeleton } from "tdesign-react";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { useUserGroupMembersQuery } from "../../user-groups/composables/_user-group-queries";
import { userDetailPath } from "../composables/_user-route";
import { UserAvatar } from "./user-avatar";

const VISIBLE_MEMBER_LIMIT = 6;

type UserDetailTeammatesProps = {
    groupLabel: string;
    groupName?: string;
    name: string;
    workspace: string;
};

export function UserDetailTeammates({ groupLabel, groupName, name, workspace }: UserDetailTeammatesProps) {
    const membersQuery = useUserGroupMembersQuery(groupName);
    const members = (membersQuery.data ?? []).filter((member) => member.name && member.name !== name);
    const visibleMembers = members.slice(0, VISIBLE_MEMBER_LIMIT);
    const loading = Boolean(groupName) && membersQuery.isPending;

    return (
        <Card
            actions={
                <span className="text-xs text-[var(--td-text-color-secondary)]">
                    {loading ? "加载中" : `${members.length} 人`}
                </span>
            }
            bordered={false}
            shadow={false}
            title="团队成员"
        >
            {loading ? <Skeleton theme="avatar-text" /> : null}
            {membersQuery.error ? (
                <Empty
                    action={
                        <Button onClick={() => void membersQuery.refetch()} size="small">
                            重试
                        </Button>
                    }
                    description={consoleErrorMessage(membersQuery.error)}
                    title="团队成员加载失败"
                    type="fail"
                />
            ) : null}
            {!groupName ? (
                <Empty description="当前用户未加入用户组" />
            ) : membersQuery.isSuccess ? (
                visibleMembers.length > 0 ? (
                    <div className="flex flex-col">
                        {visibleMembers.map((member) => {
                            const displayName = member.displayName || member.name || "未命名用户";

                            return (
                                <Link
                                    className="flex min-w-0 items-center gap-3 py-4 first:pt-0 last:pb-0"
                                    href={userDetailPath(workspace, member.name as string)}
                                    key={member.name}
                                >
                                    <UserAvatar name={displayName} size={48} />
                                    <div className="min-w-0">
                                        <div className="truncate text-sm font-medium">{displayName}</div>
                                        <div className="mt-0.5 truncate text-xs text-[var(--td-text-color-secondary)]">
                                            {member.userGroupDisplayName || groupLabel}
                                        </div>
                                    </div>
                                </Link>
                            );
                        })}
                        {members.length > visibleMembers.length ? (
                            <div className="pt-3 text-xs text-[var(--td-text-color-placeholder)]">
                                另有 {members.length - visibleMembers.length} 位同组用户
                            </div>
                        ) : null}
                    </div>
                ) : (
                    <Empty description="当前用户组暂无其他成员" />
                )
            ) : null}
        </Card>
    );
}
