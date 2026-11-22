"use client";

import { useCallback, useEffect } from "react";

import { useEventListener } from "@/hooks/utils/use-event-listener";

export type ScrollToAnchorOptions = {
    offset?: number;
    behavior?: ScrollBehavior;
};

function readHashId() {
    const hash = location.hash.slice(1);
    try {
        return decodeURIComponent(hash);
    } catch {
        return hash;
    }
}

export function useScrollToAnchor({ offset = 0, behavior = "smooth" }: ScrollToAnchorOptions = {}) {
    const scrollToAnchor = useCallback(
        (id: string, updateHash = true) => {
            const element = document.getElementById(id);
            if (!element) {
                return false;
            }

            const top = element.getBoundingClientRect().top + window.scrollY - offset;
            window.scrollTo({ top, behavior });
            if (updateHash) {
                history.pushState(null, "", `#${id}`);
            }
            return true;
        },
        [offset, behavior],
    );

    useEventListener("hashchange", () => {
        const id = readHashId();
        if (id) {
            scrollToAnchor(id, false);
        }
    });

    useEffect(() => {
        const id = readHashId();
        if (id) {
            scrollToAnchor(id, false);
        }
    }, [scrollToAnchor]);

    return { scrollToAnchor };
}
