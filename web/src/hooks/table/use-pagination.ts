"use client";

import { useCallback, useState } from "react";

export const DEFAULT_PAGE_SIZE = 20;

export type PaginationOptions = {
    initialPage?: number;
    initialPageSize?: number;
    total?: number;
};

function normalizePositiveInt(value: number, fallback: number) {
    return Number.isFinite(value) ? Math.max(1, Math.floor(value)) : fallback;
}

export function usePagination({
    initialPage = 1,
    initialPageSize = DEFAULT_PAGE_SIZE,
    total = 0,
}: PaginationOptions = {}) {
    const [pageState, setPageState] = useState(() => normalizePositiveInt(initialPage, 1));
    const [pageSizeState, setPageSizeState] = useState(() => normalizePositiveInt(initialPageSize, DEFAULT_PAGE_SIZE));

    const pageCount = Math.max(1, Math.ceil(total / pageSizeState));
    const page = Math.min(pageState, pageCount);

    const setPage = useCallback(
        (nextPage: number) => {
            setPageState(Math.min(normalizePositiveInt(nextPage, 1), pageCount));
        },
        [pageCount],
    );

    const setPageSize = useCallback((nextPageSize: number) => {
        setPageSizeState(normalizePositiveInt(nextPageSize, DEFAULT_PAGE_SIZE));
        setPageState(1);
    }, []);

    const nextPage = useCallback(() => {
        setPageState(Math.min(page + 1, pageCount));
    }, [page, pageCount]);

    const previousPage = useCallback(() => {
        setPageState(Math.max(page - 1, 1));
    }, [page]);

    return {
        page,
        pageSize: pageSizeState,
        pageCount,
        total,
        canPreviousPage: page > 1,
        canNextPage: page < pageCount,
        setPage,
        setPageSize,
        nextPage,
        previousPage,
    };
}
