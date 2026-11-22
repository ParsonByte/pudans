"use client";

import { useCallback, useRef } from "react";

import { useIsomorphicLayoutEffect } from "@/hooks/utils/use-isomorphic-layout-effect";

export function useTextareaAutosize<T extends HTMLTextAreaElement>(maxHeight?: number) {
    const ref = useRef<T>(null);

    const resize = useCallback(() => {
        const element = ref.current;
        if (!element) {
            return;
        }

        element.style.height = "auto";
        const nextHeight = maxHeight ? Math.min(element.scrollHeight, maxHeight) : element.scrollHeight;
        element.style.height = `${nextHeight}px`;
        element.style.overflowY = maxHeight && element.scrollHeight > maxHeight ? "auto" : "hidden";
    }, [maxHeight]);

    useIsomorphicLayoutEffect(() => {
        const element = ref.current;
        if (!element) {
            return;
        }

        resize();
        element.addEventListener("input", resize);
        return () => element.removeEventListener("input", resize);
    }, [resize]);

    return { ref, resize };
}
