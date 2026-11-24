"use client";

import { useFrame } from "@react-three/fiber";

import { playerRuntime } from "../../world/player-runtime";

export function RunStamina() {
    useFrame((_, delta) => {
        const moving = playerRuntime.speed > 0.4;
        const drain = playerRuntime.input.run && moving ? 0.14 : -0.18;
        playerRuntime.stamina = Math.max(0, Math.min(1, playerRuntime.stamina - drain * delta));
    });

    return null;
}
