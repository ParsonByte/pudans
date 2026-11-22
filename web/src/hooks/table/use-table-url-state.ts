"use client";

import { useCallback, useMemo } from "react";

import type { PaginationState, SortingState } from "@tanstack/react-table";

import { DEFAULT_PAGE_SIZE } from "@/hooks/table/use-pagination";
import { TABLE_FILTER_PREFIX, parseTableFilters, writeTableFilter } from "@/hooks/table/use-table-filters";
import { useQueryParams } from "@/hooks/utils/use-query-params";

const PAGE_KEY = "page";
const PAGE_SIZE_KEY = "pageSize";
const SORT_KEY = "sort";
const ORDER_KEY = "order";

export type TableUrlStateOptions = {
    defaultPageSize?: number;
};

function parsePositiveInt(value: string | null, fallback: number) {
    const parsed = Number.parseInt(value ?? "", 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

export function useTableUrlState({ defaultPageSize = DEFAULT_PAGE_SIZE }: TableUrlStateOptions = {}) {
    const { searchParams, updateParams } = useQueryParams();

    const page = parsePositiveInt(searchParams.get(PAGE_KEY), 1);
    const pageSize = parsePositiveInt(searchParams.get(PAGE_SIZE_KEY), defaultPageSize);
    const sortColumn = searchParams.get(SORT_KEY);
    const sortDesc = searchParams.get(ORDER_KEY) === "desc";

    const filters = useMemo(() => parseTableFilters(searchParams), [searchParams]);

    const pagination = useMemo<PaginationState>(() => ({ pageIndex: page - 1, pageSize }), [page, pageSize]);
    const sorting = useMemo<SortingState>(
        () => (sortColumn ? [{ id: sortColumn, desc: sortDesc }] : []),
        [sortColumn, sortDesc],
    );

    const setPage = useCallback(
        (nextPage: number) => {
            updateParams((params) => {
                if (nextPage <= 1) {
                    params.delete(PAGE_KEY);
                } else {
                    params.set(PAGE_KEY, String(nextPage));
                }
            });
        },
        [updateParams],
    );

    const setPageSize = useCallback(
        (nextPageSize: number) => {
            updateParams((params) => {
                if (nextPageSize === defaultPageSize) {
                    params.delete(PAGE_SIZE_KEY);
                } else {
                    params.set(PAGE_SIZE_KEY, String(nextPageSize));
                }
                params.delete(PAGE_KEY);
            });
        },
        [updateParams, defaultPageSize],
    );

    const setSort = useCallback(
        (column: string | null, desc = false) => {
            updateParams((params) => {
                if (column) {
                    params.set(SORT_KEY, column);
                    params.set(ORDER_KEY, desc ? "desc" : "asc");
                } else {
                    params.delete(SORT_KEY);
                    params.delete(ORDER_KEY);
                }
                params.delete(PAGE_KEY);
            });
        },
        [updateParams],
    );

    const setFilter = useCallback(
        (key: string, value: string | null) => {
            updateParams((params) => {
                writeTableFilter(params, key, value);
                params.delete(PAGE_KEY);
            });
        },
        [updateParams],
    );

    const reset = useCallback(() => {
        updateParams((params) => {
            params.delete(PAGE_KEY);
            params.delete(PAGE_SIZE_KEY);
            params.delete(SORT_KEY);
            params.delete(ORDER_KEY);
            for (const key of [...params.keys()]) {
                if (key.startsWith(TABLE_FILTER_PREFIX)) {
                    params.delete(key);
                }
            }
        });
    }, [updateParams]);

    return {
        pagination,
        sorting,
        filters,
        setPage,
        setPageSize,
        setSort,
        setFilter,
        reset,
    };
}
