"use client";

import { useVirtualizer as useVirtualizerBase } from "@tanstack/react-virtual";

const DEFAULT_ESTIMATE_SIZE = () => 40;

export type TableVirtualizerOptions<TScrollElement extends Element> = {
    count: number;
    getScrollElement: () => TScrollElement | null;
    estimateSize?: (index: number) => number;
    overscan?: number;
};

export function useVirtualizer<TScrollElement extends Element>({
    count,
    getScrollElement,
    estimateSize = DEFAULT_ESTIMATE_SIZE,
    overscan = 10,
}: TableVirtualizerOptions<TScrollElement>) {
    return useVirtualizerBase({
        count,
        getScrollElement,
        estimateSize,
        overscan,
    });
}
