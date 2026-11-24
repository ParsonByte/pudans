"use client";

import { useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import { useDreamStore } from "../../stores/use-dream-store";
import { cameraRuntime } from "../../world/camera-runtime";

export function CameraFocusAnimator() {
    const selectedId = useDreamStore((state) => state.selectedMemoryId);
    const selected = useDreamStore((state) =>
        selectedId ? state.world?.memories.find((memory) => memory.id === selectedId) : undefined,
    );
    const target = useRef(new THREE.Vector3()).current;

    useFrame((_, delta) => {
        if (!selected) {
            cameraRuntime.focusPoint = null;
            return;
        }

        target.set(...selected.position);
        cameraRuntime.focusPoint = target;
        const blend = Math.min(1, delta * 2.2);
        cameraRuntime.lookTarget.lerp(target, blend * 0.16);
    });

    return null;
}
