"use client";

import { useEffect } from "react";

import { useDreamStore } from "../../stores/use-dream-store";

export function ParticleBudgetController() {
    const performanceMode = useDreamStore((state) => state.performanceMode);
    const reducedMotion = useDreamStore((state) => state.reducedMotion);

    useEffect(() => {
        document.documentElement.dataset.dreamParticles = performanceMode ? "low" : reducedMotion ? "calm" : "full";
    }, [performanceMode, reducedMotion]);

    return null;
}
