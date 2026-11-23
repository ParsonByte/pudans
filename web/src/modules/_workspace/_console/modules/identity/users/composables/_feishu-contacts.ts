"use client";

import { type FeishuContactPage, type FeishuContactView, apiClient } from "@pudans-dev/api-client";
import { type InfiniteData, useInfiniteQuery } from "@tanstack/react-query";

const CONTACT_PAGE_SIZE = 50;

export const FEISHU_CONTACTS_QUERY_KEY = ["console", "feishu", "contacts"] as const;

export function feishuContactImportIssue(contact: FeishuContactView): string | undefined {
    if (contact.imported) {
        return "已导入";
    }
    if (!contact.providerUserId) {
        return "缺少用户编号";
    }
    if (!contact.displayName?.trim()) {
        return "缺少姓名";
    }

    return undefined;
}

export function useFeishuContacts(enabled: boolean) {
    const query = useInfiniteQuery<
        FeishuContactPage,
        Error,
        InfiniteData<FeishuContactPage>,
        typeof FEISHU_CONTACTS_QUERY_KEY,
        string | null
    >({
        enabled,
        queryFn: async ({ pageParam }) =>
            (
                await apiClient.user.listFeishuContacts({
                    pageSize: CONTACT_PAGE_SIZE,
                    pageToken: pageParam ?? undefined,
                })
            ).data,
        initialPageParam: null as string | null,
        getNextPageParam: (lastPage) => (lastPage.hasMore && lastPage.pageToken ? lastPage.pageToken : null),
        queryKey: FEISHU_CONTACTS_QUERY_KEY,
    });

    return query;
}
