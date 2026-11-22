"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export type StickyScrollOptions = {
    enabled?: boolean;
    threshold?: number;
};

export function useStickyScroll<T extends HTMLElement>({ enabled = true, threshold = 24 }: StickyScrollOptions = {}) {
    const ref = useRef<T>(null);
    const [isAtBottom, setIsAtBottom] = useState(true);
    const followingRef = useRef(true);

    const scrollToBottom = useCallback((behavior: ScrollBehavior = "smooth") => {
        const element = ref.current;
        if (!element) {
            return;
        }

        followingRef.current = true;
        setIsAtBottom(true);
        element.scrollTo({ top: element.scrollHeight, behavior });
    }, []);

    useEffect(() => {
        const element = ref.current;
        if (!element) {
            return;
        }

        const handleScroll = () => {
            const distance = element.scrollHeight - element.scrollTop - element.clientHeight;
            const atBottom = distance <= threshold;
            followingRef.current = atBottom;
            setIsAtBottom(atBottom);
        };

        element.addEventListener("scroll", handleScroll, { passive: true });
        return () => element.removeEventListener("scroll", handleScroll);
    }, [threshold]);

    useEffect(() => {
        if (!enabled) {
            return;
        }

        const element = ref.current;
        if (!element) {
            return;
        }

        if (followingRef.current) {
            element.scrollTop = element.scrollHeight;
        }

        const observer = new MutationObserver(() => {
            if (followingRef.current) {
                element.scrollTop = element.scrollHeight;
            }
        });

        observer.observe(element, { childList: true, subtree: true, characterData: true });
        return () => observer.disconnect();
    }, [enabled]);

    return { ref, isAtBottom, isFollowing: !isAtBottom, scrollToBottom };
}
