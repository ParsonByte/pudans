"use client";

import { useEffect } from "react";

import { useThree } from "@react-three/fiber";

import { useDreamStore } from "../../stores/use-dream-store";

export function QualityAutoScaler() {
    const setDpr = useThree((state) => state.setDpr);
    const performanceMode = useDreamStore((state) => state.performanceMode);
    const reducedMotion = useDreamStore((state) => state.reducedMotion);

    useEffect(() => {
        setDpr(performanceMode ? 0.8 : reducedMotion ? 1 : [1, 1.5]);
    }, [performanceMode, reducedMotion, setDpr]);

    return null;
}
