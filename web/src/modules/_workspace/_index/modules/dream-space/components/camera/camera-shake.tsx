"use client";

import { useFrame } from "@react-three/fiber";

import { cameraRuntime } from "../../world/camera-runtime";

export function CameraShake({ reducedMotion }: { reducedMotion: boolean }) {
    useFrame((_, delta) => {
        cameraRuntime.shake = Math.max(0, cameraRuntime.shake - delta * 2.2);

        if (reducedMotion) {
            cameraRuntime.shake = 0;
        }
    });

    return null;
}
