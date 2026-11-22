"use client";

import { useCallback, useMemo } from "react";

import { useQueryParams } from "@/hooks/utils/use-query-params";

// 筛选参数使用独立前缀，避免与分页、排序参数冲突。
export const TABLE_FILTER_PREFIX = "filter.";

export function parseTableFilters(searchParams: Pick<URLSearchParams, "entries">) {
    const filters: Record<string, string> = {};
    for (const [key, value] of searchParams.entries()) {
        if (key.startsWith(TABLE_FILTER_PREFIX)) {
            filters[key.slice(TABLE_FILTER_PREFIX.length)] = value;
        }
    }
    return filters;
}

export function writeTableFilter(params: URLSearchParams, key: string, value: string | null) {
    const paramKey = `${TABLE_FILTER_PREFIX}${key}`;
    if (value) {
        params.set(paramKey, value);
    } else {
        params.delete(paramKey);
    }
}

export function useTableFilters() {
    const { searchParams, updateParams } = useQueryParams();
    const filters = useMemo(() => parseTableFilters(searchParams), [searchParams]);

    const setFilter = useCallback(
        (key: string, value: string | null) => {
            updateParams((params) => writeTableFilter(params, key, value));
        },
        [updateParams],
    );

    const clearFilters = useCallback(() => {
        updateParams((params) => {
            for (const key of [...params.keys()]) {
                if (key.startsWith(TABLE_FILTER_PREFIX)) {
                    params.delete(key);
                }
            }
        });
    }, [updateParams]);

    return { filters, setFilter, clearFilters };
}
