"use client";

import { useCallback } from "react";

import { usePathname, useRouter, useSearchParams } from "next/navigation";

export type QueryParamValue = string | number | boolean | null | undefined;

export function useQueryParams() {
    const router = useRouter();
    const pathname = usePathname();
    const searchParams = useSearchParams();

    const commit = useCallback(
        (mutate: (params: URLSearchParams) => void) => {
            const params = new URLSearchParams(searchParams.toString());
            mutate(params);

            const query = params.toString();
            if (query === searchParams.toString()) {
                return;
            }

            router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
        },
        [pathname, router, searchParams],
    );

    const setParam = useCallback(
        (key: string, value: QueryParamValue) => {
            commit((params) => {
                if (value === null || value === undefined || value === "") {
                    params.delete(key);
                } else {
                    params.set(key, String(value));
                }
            });
        },
        [commit],
    );

    const setParams = useCallback(
        (values: Record<string, QueryParamValue>) => {
            commit((params) => {
                for (const [key, value] of Object.entries(values)) {
                    if (value === null || value === undefined || value === "") {
                        params.delete(key);
                    } else {
                        params.set(key, String(value));
                    }
                }
            });
        },
        [commit],
    );

    const removeParam = useCallback(
        (key: string) => {
            commit((params) => params.delete(key));
        },
        [commit],
    );

    const clearParams = useCallback(() => {
        if (!searchParams.toString()) {
            return;
        }
        router.replace(pathname, { scroll: false });
    }, [pathname, router, searchParams]);

    return {
        searchParams,
        updateParams: commit,
        setParam,
        setParams,
        removeParam,
        clearParams,
    };
}
