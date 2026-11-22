"use client";

import { useCallback, useState } from "react";

export function useFileParser() {
    const [pending, setPending] = useState(0);
    const [error, setError] = useState<Error | null>(null);

    const run = useCallback(async <T>(task: () => Promise<T>) => {
        setPending((count) => count + 1);
        setError(null);

        try {
            return await task();
        } catch (cause) {
            const normalized = cause instanceof Error ? cause : new Error(String(cause));
            setError(normalized);
            throw normalized;
        } finally {
            setPending((count) => Math.max(0, count - 1));
        }
    }, []);

    const parseSpreadsheet = useCallback(
        (file: File) =>
            run(async () => {
                const XLSX = await import("xlsx");
                const data = await file.arrayBuffer();
                return XLSX.read(data, { type: "array" });
            }),
        [run],
    );

    const unzip = useCallback(
        (file: File) =>
            run(async () => {
                const { unzipSync } = await import("fflate");
                const data = await file.arrayBuffer();
                return unzipSync(new Uint8Array(data));
            }),
        [run],
    );

    const renderDocx = useCallback(
        (file: File, container: HTMLElement) =>
            run(async () => {
                const { renderAsync } = await import("docx-preview");
                const data = await file.arrayBuffer();
                await renderAsync(data, container);
            }),
        [run],
    );

    return {
        loading: pending > 0,
        error,
        parseSpreadsheet,
        unzip,
        renderDocx,
    };
}
