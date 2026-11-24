"use client";

import { useFrame } from "@react-three/fiber";

import { useDreamStore } from "../../stores/use-dream-store";

export function CinematicCamera() {
    const paused = useDreamStore((state) => state.paused);

    useFrame(() => {
        if (paused) {
            document.documentElement.dataset.dreamCinematic = "paused";
        } else {
            delete document.documentElement.dataset.dreamCinematic;
        }
    });

    return null;
}
