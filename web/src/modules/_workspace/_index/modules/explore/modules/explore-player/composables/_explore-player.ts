"use client";

import { useEffect } from "react";

export const NEXT_VIDEO_KEYS = Array.from({ length: 9 }, (_, index) => `explore-next-video-${index}`);
export const ACTIVE_NEXT_VIDEO_INDEX = 0;

export function useExplorePlayerBodyLock(): void {
    useEffect(() => {
        const previousOverflow = document.body.style.overflow;

        document.body.style.overflow = "hidden";

        return () => {
            document.body.style.overflow = previousOverflow;
        };
    }, []);
}
