"use client";

import type { TablePaginationConfig } from "antd";

import { useTableUrlState } from "@/hooks/table/use-table-url-state";
import { useMobile } from "@/hooks/ui/use-mobile";

export function useConsoleTableState(defaultPageSize = 20) {
    const state = useTableUrlState({ defaultPageSize });
    const isMobile = useMobile();
    const page = state.pagination.pageIndex + 1;
    const pageSize = state.pagination.pageSize;
    const keyword = state.filters.keyword ?? "";

    const pagination: TablePaginationConfig = {
        current: page,
        pageSize,
        showLessItems: isMobile,
        showSizeChanger: !isMobile,
        showTotal: (total) => `共 ${total} 条`,
        size: isMobile ? "small" : undefined,
        onChange: (nextPage, nextPageSize) => {
            if (nextPageSize !== pageSize) {
                state.setPageSize(nextPageSize);
                return;
            }

            state.setPage(nextPage);
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
