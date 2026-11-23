"use client";

import type { ListedUser, UserGroup } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

import { useDebounce } from "@/hooks/utils/use-debounce";

const USER_GROUP_PAGE_SIZE = 500;
const USER_GROUP_MEMBER_PAGE_SIZE = 500;
const USER_SEARCH_PAGE_SIZE = 20;

export function useUserGroupsQuery() {
    return useQuery({
        queryKey: ["console", "user-groups", "all"],
        queryFn: async () => {
            const groups: UserGroup[] = [];

            for (let page = 1; ; page += 1) {
                const result = (await apiClient.userGroup.listUserGroup({ page, size: USER_GROUP_PAGE_SIZE })).data;
                groups.push(...(result.items ?? []));
                if (!result.hasNext) {
                    break;
                }
            }

            return groups.filter((group) => !group.metadata.deletionTimestamp);
        },
        refetchOnWindowFocus: false,
        staleTime: 60_000,
    });
}

export function useUserGroupMembersQuery(groupName?: string) {
    return useQuery({
        enabled: Boolean(groupName),
        queryKey: ["console", "user-groups", "members", groupName],
        queryFn: async () => {
            const members: ListedUser[] = [];

            for (let page = 1; ; page += 1) {
                const result = (
                    await apiClient.user.listUsers({
                        page,
                        size: USER_GROUP_MEMBER_PAGE_SIZE,
                        userGroup: groupName,
                    })
                ).data;
                const items = result.items ?? [];
                members.push(...items);
                if (
                    items.length < USER_GROUP_MEMBER_PAGE_SIZE ||
                    (result.total !== undefined && members.length >= result.total)
                ) {
                    break;
                }
            }

            return members;
        },
        refetchOnWindowFocus: false,
        staleTime: 30_000,
    });
}

export function useUserGroupUserSearchQuery(keyword: string) {
    const query = useDebounce(keyword.trim(), 300);

    return useQuery({
        queryKey: ["console", "users", "group-search", query],
        queryFn: async () => {
            const result = (
                await apiClient.user.listUsers({
                    keyword: query || undefined,
                    page: 1,
                    size: USER_SEARCH_PAGE_SIZE,
                })
            ).data;

            return (result.items ?? []).filter((user): user is ListedUser & { name: string } => Boolean(user.name));
        },
        refetchOnWindowFocus: false,
        staleTime: 30_000,
    });
}
