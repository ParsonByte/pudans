"use client";

import { useCallback } from "react";

export function useDownload() {
    const download = useCallback((data: BlobPart, filename: string, type = "application/octet-stream") => {
        const blob = data instanceof Blob ? data : new Blob([data], { type });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");

        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 0);
    }, []);

    return { download };
}
