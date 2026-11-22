"use client";

import { useState } from "react";

export function useDialogState<T = unknown>() {
    const [open, setOpen] = useState(false);
    const [currentRow, setCurrentRow] = useState<T | null>(null);

    function changeOpen(nextOpen: boolean) {
        setOpen(nextOpen);
        // 关闭后清空当前行，避免下次打开沿用旧数据。
        if (!nextOpen) {
            setCurrentRow(null);
        }
    }

    return {
        open,
        currentRow,
        setOpen: changeOpen,
        setCurrentRow,
    };
}
