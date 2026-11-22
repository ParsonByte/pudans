"use client";

import { useCallback } from "react";

import type { ColumnVisibilityState } from "@tanstack/react-table";

import { useLocalStorage } from "@/hooks/utils/use-local-storage";

const EMPTY_VISIBILITY: ColumnVisibilityState = {};

export function useColumnVisibility(storageKey: string, initialVisibility: ColumnVisibilityState = EMPTY_VISIBILITY) {
    const [columnVisibility, setColumnVisibility] = useLocalStorage<ColumnVisibilityState>(
        storageKey,
        initialVisibility,
    );

    const toggleColumn = useCallback(
        (columnId: string) => {
            setColumnVisibility((previous) => {
                const next = { ...previous };
                if (previous[columnId] === false) {
                    delete next[columnId];
                } else {
                    next[columnId] = false;
                }
                return next;
            });
        },
        [setColumnVisibility],
    );

    const resetColumns = useCallback(() => {
        setColumnVisibility(EMPTY_VISIBILITY);
    }, [setColumnVisibility]);

    return { columnVisibility, setColumnVisibility, toggleColumn, resetColumns };
}
