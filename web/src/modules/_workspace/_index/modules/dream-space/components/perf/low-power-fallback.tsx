"use client";

import { useEffect } from "react";

import { useDreamStore } from "../../stores/use-dream-store";

export function LowPowerFallback({ lowPower, webgl }: { lowPower: boolean; webgl: boolean }) {
    const performanceMode = useDreamStore((state) => state.performanceMode);
    const reducedMotion = useDreamStore((state) => state.reducedMotion);

    useEffect(() => {
        document.documentElement.dataset.dreamQuality = !webgl
            ? "fallback"
            : performanceMode || lowPower
              ? "low"
              : reducedMotion
                ? "calm"
                : "high";
    }, [lowPower, performanceMode, reducedMotion, webgl]);

    return null;
}
