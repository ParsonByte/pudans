"use client";

import { useFrame } from "@react-three/fiber";

import { skyRuntime } from "../../world/sky-runtime";

export function StarTwinkleController({ reducedMotion }: { reducedMotion: boolean }) {
    useFrame(({ clock }) => {
        skyRuntime.twinkle = reducedMotion ? 0.35 : 0.78 + Math.sin(clock.elapsedTime * 0.16) * 0.22;
    });

    return null;
}
