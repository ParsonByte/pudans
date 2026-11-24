"use client";

import { useMemo, useState } from "react";

import type { UserGroup } from "@pudans-dev/api-client";
import { SearchIcon } from "tdesign-icons-react";
import { Button, Input, Skeleton, Tree } from "tdesign-react";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { userGroupDescription, userGroupDisplayName } from "../../user-groups/composables/_user-group";

const ALL_USERS_NODE = "all-users";

type UserGroupTreeProps = {
    error?: unknown;
    groups: UserGroup[];
    loading: boolean;
    onRetry?: () => void;
    onSelect: (groupName?: string) => void;
    selectedName?: string;
};

type UserGroupTreeNode = {
    children?: UserGroupTreeNode[];
    label: string;
    value: string;
};

export function UserGroupTree({ error, groups, loading, onRetry, onSelect, selectedName }: UserGroupTreeProps) {
    const [keyword, setKeyword] = useState("");
    const filteredGroups = useMemo(() => {
        const query = keyword.trim().toLocaleLowerCase();

        if (!query) {
            return groups;
        }

        return groups.filter((group) =>
            [userGroupDisplayName(group), group.metadata.name, userGroupDescription(group)]
                .join(" ")
                .toLocaleLowerCase()
                .includes(query),
        );
    }, [groups, keyword]);
    const treeData = useMemo<UserGroupTreeNode[]>(
        () => [
            {
                children: filteredGroups.map((group) => ({
                    label: userGroupDisplayName(group),
                    value: group.metadata.name,
                })),
                label: "全部用户",
                value: ALL_USERS_NODE,
            },
        ],
        [filteredGroups],
    );
    return (
        <div className="min-w-0">
            <Input
                clearable
                onChange={(value) => setKeyword(String(value))}
                placeholder="请输入关键词"
                suffixIcon={<SearchIcon />}
                value={keyword}
            />
            <div className="mt-6 max-h-[280px] min-h-[220px] overflow-y-auto lg:max-h-[calc(100vh-250px)]">
                {loading ? (
                    <div className="flex flex-col gap-2">
                        {Array.from({ length: 5 }).map((_, index) => (
                            <Skeleton animation="gradient" key={index} theme="avatar-text" />
                        ))}
                    </div>
                ) : error ? (
                    <div className="flex flex-col items-center gap-3 py-8 text-center">
                        <span className="text-xs text-[var(--td-text-color-secondary)]">
                            {consoleErrorMessage(error)}
                        </span>
                        {onRetry ? (
                            <Button onClick={onRetry} size="small" variant="outline">
                                重试
                            </Button>
                        ) : null}
                    </div>
                ) : (
                    <>
                        <Tree
                            activable
                            actived={[selectedName || ALL_USERS_NODE]}
                            data={treeData}
                            expandAll
                            hover
                            onActive={(value) => {
                                const next = value[0];

                                if (!next) {
                                    return;
                                }

                                onSelect(next === ALL_USERS_NODE ? undefined : String(next));
                            }}
                            transition
                        />
                        {filteredGroups.length === 0 ? (
                            <div className="py-4 text-center text-xs text-[var(--td-text-color-placeholder)]">
                                未找到匹配的用户组
                            </div>
                        ) : null}
                    </>
                )}
            </div>
        </div>
    );
}
