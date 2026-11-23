"use client";

import type { PaginationProps } from "tdesign-react";

import { useTableUrlState } from "@/hooks/table/use-table-url-state";
import { useMobile } from "@/hooks/ui/use-mobile";

export function useConsoleTableState(defaultPageSize = 20) {
    const state = useTableUrlState({ defaultPageSize });
    const isMobile = useMobile();
    const page = state.pagination.pageIndex + 1;
    const pageSize = state.pagination.pageSize;
    const keyword = state.filters.keyword ?? "";

    const pagination: PaginationProps = {
        current: page,
        pageSize,
        showJumper: false,
        showPageSize: !isMobile,
        size: isMobile ? "small" : "medium",
        onChange: (pageInfo) => {
            if (pageInfo.pageSize !== pageSize) {
                state.setPageSize(pageInfo.pageSize);
                return;
            }

            state.setPage(pageInfo.current);
        },
    };

    return {
        ...state,
        keyword,
        page,
        pageSize,
        pagination,
        setKeyword: (value: string) => state.setFilter("keyword", value.trim() || null),
    };
}
