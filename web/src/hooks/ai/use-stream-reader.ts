"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { useEventCallback } from "@/hooks/utils/use-event-callback";

export type StreamReaderOptions = {
    onChunk?: (chunk: string) => void;
    onDone?: (text: string) => void;
    onError?: (error: Error) => void;
};

export function useStreamReader({ onChunk, onDone, onError }: StreamReaderOptions = {}) {
    const [text, setText] = useState("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);
    const abortRef = useRef<AbortController | null>(null);

    const handleChunk = useEventCallback((chunk: string) => onChunk?.(chunk));
    const handleDone = useEventCallback((result: string) => onDone?.(result));
    const handleError = useEventCallback((cause: Error) => onError?.(cause));

    const read = useCallback(
        async (input: RequestInfo | URL, init?: RequestInit) => {
            abortRef.current?.abort();
            const controller = new AbortController();
            abortRef.current = controller;

            setLoading(true);
            setError(null);
            setText("");

            try {
                const response = await fetch(input, { ...init, signal: controller.signal });
                if (!response.ok) {
                    throw new Error(`请求失败：${response.status} ${response.statusText}`);
                }
                if (!response.body) {
                    throw new Error("响应不包含可读取的流。");
                }

                const reader = response.body.getReader();
                const decoder = new TextDecoder();
                let accumulated = "";

                while (true) {
                    const { done, value } = await reader.read();
                    if (done) {
                        break;
                    }

                    const chunk = decoder.decode(value, { stream: true });
                    if (chunk) {
                        accumulated += chunk;
                        setText(accumulated);
                        handleChunk(chunk);
                    }
                }

                const tail = decoder.decode();
                if (tail) {
                    accumulated += tail;
                    setText(accumulated);
                    handleChunk(tail);
                }

                handleDone(accumulated);
            } catch (cause) {
                if (controller.signal.aborted) {
                    return;
                }

                const normalized = cause instanceof Error ? cause : new Error(String(cause));
                setError(normalized);
                handleError(normalized);
            } finally {
                if (!controller.signal.aborted) {
                    setLoading(false);
                }
                if (abortRef.current === controller) {
                    abortRef.current = null;
                }
            }
        },
        [handleChunk, handleDone, handleError],
    );

    const abort = useCallback(() => {
        abortRef.current?.abort();
        abortRef.current = null;
        setLoading(false);
    }, []);

    useEffect(() => {
        return () => abortRef.current?.abort();
    }, []);

    return { text, loading, error, read, abort };
}
