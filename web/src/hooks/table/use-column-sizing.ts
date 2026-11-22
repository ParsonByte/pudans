"use client";

import { useCallback, useState } from "react";

import type { ColumnSizingState } from "@tanstack/react-table";

export function useColumnSizing(initialSizing: ColumnSizingState = {}) {
    const [columnSizing, setColumnSizing] = useState<ColumnSizingState>(initialSizing);

    const resetColumnSizing = useCallback(() => {
        setColumnSizing({});
    }, []);

    return { columnSizing, setColumnSizing, resetColumnSizing };
}
