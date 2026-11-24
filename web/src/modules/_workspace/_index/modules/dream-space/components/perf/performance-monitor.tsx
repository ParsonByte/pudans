"use client";

import { useRef } from "react";

import { useFrame } from "@react-three/fiber";

import { setDreamPerformanceMode } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";

const SAMPLE_FRAMES = 180;

export function PerformanceMonitor() {
    const frames = useRef(0);
    const elapsed = useRef(0);
    const performanceMode = useDreamStore((state) => state.performanceMode);

    useFrame((_, delta) => {
        if (performanceMode) {
            return;
        }

        frames.current += 1;
        elapsed.current += delta;

        if (frames.current < SAMPLE_FRAMES) {
            return;
        }

        const fps = frames.current / Math.max(0.01, elapsed.current);
        document.documentElement.dataset.dreamFps = fps.toFixed(1);
        frames.current = 0;
        elapsed.current = 0;

        if (fps < 30 && !document.hidden) {
            setDreamPerformanceMode(true);
        }
    });

    return null;
}
