"use client";

import { useEffect } from "react";

import { usePathname, useRouter, useSearchParams } from "next/navigation";

import {
    INDEX_LEGACY_PAGE_PARAM,
    INDEX_NAV_GROUP,
    INDEX_SESSION_PARAM,
    type IndexPageKey,
    indexPageHref,
    resolveIndexPage,
} from "@/routes/(_workspace)/(_index)/paths";

export function useIndexRoute(): IndexPageKey {
    const pathname = usePathname();
    const router = useRouter();
    const searchParams = useSearchParams();
    const page = resolveIndexPage(
        pathname,
        searchParams.get(INDEX_NAV_GROUP),
        searchParams.get(INDEX_LEGACY_PAGE_PARAM),
    );
    const search = searchParams.toString();
    const sessionIds = searchParams.getAll(INDEX_SESSION_PARAM);
    const canonicalPath = indexPageHref(page, sessionIds);

    useEffect(() => {
        const currentPath = search ? `${pathname}?${search}` : pathname;

        if (currentPath !== canonicalPath) {
            router.replace(canonicalPath);
        }
    }, [canonicalPath, pathname, router, search]);

    return page;
}
