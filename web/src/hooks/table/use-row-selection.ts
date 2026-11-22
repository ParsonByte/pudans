"use client";

import { useCallback, useMemo, useState } from "react";

import type { RowSelectionState } from "@tanstack/react-table";

const EMPTY_ROW_IDS: string[] = [];

export type RowSelectionOptions = {
    rowIds?: string[];
    initialSelection?: RowSelectionState;
    onChange?: (selection: RowSelectionState) => void;
};

export function useRowSelection({ rowIds = EMPTY_ROW_IDS, initialSelection, onChange }: RowSelectionOptions = {}) {
    const [rowSelection, setRowSelection] = useState<RowSelectionState>(initialSelection ?? {});

    const selectedIds = useMemo(() => Object.keys(rowSelection).filter((id) => rowSelection[id]), [rowSelection]);
    const isAllSelected = rowIds.length > 0 && rowIds.every((id) => rowSelection[id]);
    const isSomeSelected = !isAllSelected && rowIds.some((id) => rowSelection[id]);
    const isSelected = useCallback((id: string) => Boolean(rowSelection[id]), [rowSelection]);

    const toggleRow = useCallback(
        (id: string, selected?: boolean) => {
            const next = { ...rowSelection };
            const nextSelected = selected ?? !rowSelection[id];
            if (nextSelected) {
                next[id] = true;
            } else {
                delete next[id];
            }
            setRowSelection(next);
            onChange?.(next);
        },
        [rowSelection, onChange],
    );

    const toggleAll = useCallback(
        (selected: boolean) => {
            const next = { ...rowSelection };
            for (const id of rowIds) {
                if (selected) {
                    next[id] = true;
                } else {
                    delete next[id];
                }
            }
            setRowSelection(next);
            onChange?.(next);
        },
        [rowSelection, rowIds, onChange],
    );

    const clearSelection = useCallback(() => {
        setRowSelection({});
        onChange?.({});
    }, [onChange]);

    return {
        rowSelection,
        selectedIds,
        selectedCount: selectedIds.length,
        isAllSelected,
        isSomeSelected,
        isSelected,
        setRowSelection,
        toggleRow,
        toggleAll,
        clearSelection,
    };
}
