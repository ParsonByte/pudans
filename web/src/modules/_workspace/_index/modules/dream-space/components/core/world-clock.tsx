"use client";

import { useEffect } from "react";

import { useDreamStore } from "../../stores/use-dream-store";

export function WorldClock() {
    const world = useDreamStore((state) => state.world);

    useEffect(() => {
        if (!world || typeof window === "undefined") {
            return;
        }

        const timer = window.setInterval(() => {
            document.documentElement.dataset.dreamClock = new Date().toISOString();
        }, 60_000);

        return () => window.clearInterval(timer);
    }, [world]);

    return null;
}
