"use client";

import { useRef } from "react";

import { useFrame } from "@react-three/fiber";

import { playerRuntime } from "../../world/player-runtime";

export function FootstepController() {
    const accumulated = useRef(0);

    useFrame((_, delta) => {
        if (!playerRuntime.grounded || playerRuntime.speed < 0.4) {
            return;
        }

        accumulated.current += playerRuntime.speed * delta;

        if (accumulated.current > (playerRuntime.input.run ? 2.4 : 1.8)) {
            accumulated.current = 0;
            window.dispatchEvent(
                new CustomEvent("dans-dream:footstep", { detail: { running: playerRuntime.input.run } }),
            );
        }
    });

    return null;
}
