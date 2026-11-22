"use client";

import { useCallback, useRef, useState } from "react";

import { useStreamReader } from "@/hooks/ai/use-stream-reader";

export type SseEvent = {
    event: string;
    data: string;
    id?: string;
};

export type SseOptions = {
    onEvent?: (event: SseEvent) => void;
};

function parseSseBlock(block: string): SseEvent | null {
    let event = "message";
    let id: string | undefined;
    const dataLines: string[] = [];

    for (const line of block.split(/\r?\n/)) {
        if (!line || line.startsWith(":")) {
            continue;
        }

        const separator = line.indexOf(":");
        const field = separator === -1 ? line : line.slice(0, separator);
        const value = separator === -1 ? "" : line.slice(separator + 1).replace(/^ /, "");

        if (field === "event") {
            event = value;
        } else if (field === "data") {
            dataLines.push(value);
        } else if (field === "id") {
            id = value;
        }
    }

    if (dataLines.length === 0) {
        return null;
    }

    return { event, data: dataLines.join("\n"), id };
}

export function useSse({ onEvent }: SseOptions = {}) {
    const bufferRef = useRef("");
    const [lastEvent, setLastEvent] = useState<SseEvent | null>(null);

    const emitEvent = useCallback(
        (event: SseEvent) => {
            setLastEvent(event);
            onEvent?.(event);
        },
        [onEvent],
    );

    const flush = useCallback(() => {
        const remaining = bufferRef.current;
        bufferRef.current = "";
        if (!remaining.trim()) {
            return;
        }

        const event = parseSseBlock(remaining);
        if (event) {
            emitEvent(event);
        }
    }, [emitEvent]);

    const {
        text,
        loading,
        error,
        read: readStream,
        abort,
    } = useStreamReader({
        onChunk: (chunk) => {
            bufferRef.current += chunk;
            const blocks = bufferRef.current.split(/\r?\n\r?\n/);
            bufferRef.current = blocks.pop() ?? "";

            for (const block of blocks) {
                const event = parseSseBlock(block);
                if (event) {
                    emitEvent(event);
                }
            }
        },
        onDone: flush,
    });

    const read = useCallback(
        async (input: RequestInfo | URL, init?: RequestInit) => {
            bufferRef.current = "";
            setLastEvent(null);
            return readStream(input, init);
        },
        [readStream],
    );

    const reset = useCallback(() => {
        bufferRef.current = "";
        setLastEvent(null);
    }, []);

    return { text, loading, error, lastEvent, read, abort, reset };
}
