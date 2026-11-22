"use client";

import { useEffect, useRef, useState } from "react";

export type IntersectionObserverOptions = {
    root?: Element | Document | null;
    rootMargin?: string;
    threshold?: number | number[];
    freezeOnceVisible?: boolean;
};

export function useIntersectionObserver<T extends Element>({
    root = null,
    rootMargin = "0px",
    threshold = 0,
    freezeOnceVisible = false,
}: IntersectionObserverOptions = {}) {
    const ref = useRef<T>(null);
    const [entry, setEntry] = useState<IntersectionObserverEntry | null>(null);
    const frozen = freezeOnceVisible && Boolean(entry?.isIntersecting);

    useEffect(() => {
        const element = ref.current;
        if (!element || frozen) {
            return;
        }

        const observer = new IntersectionObserver(
            ([nextEntry]) => {
                if (nextEntry) {
                    setEntry(nextEntry);
                }
            },
            { root, rootMargin, threshold },
        );

        observer.observe(element);
        return () => observer.disconnect();
    }, [root, rootMargin, threshold, frozen]);

    return {
        ref,
        entry,
        isIntersecting: entry?.isIntersecting ?? false,
    };
}
