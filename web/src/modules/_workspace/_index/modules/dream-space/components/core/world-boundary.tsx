"use client";

import { useFrame } from "@react-three/fiber";
import { useRapier } from "@react-three/rapier";

import { setDreamPlayerPosition } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import { playerRuntime } from "../../world/player-runtime";
import { regionById } from "../../world/world-generator";

export function WorldBoundary() {
    const { world: physicsWorld } = useRapier();
    const world = useDreamStore((state) => state.world);
    const activeRegionId = useDreamStore((state) => state.activeRegionId);

    useFrame(() => {
        if (!world || !playerRuntime.colliderHandle) {
            return;
        }

        const region = regionById(world, activeRegionId);
        const dx = playerRuntime.position.x - region.center[0];
        const dz = playerRuntime.position.z - region.center[2];
        const distance = Math.hypot(dx, dz);
        const edge = region.radius - 1.8;
        let next: [number, number, number] | null = null;

        if (distance > edge) {
            const scale = edge / distance;
            next = [region.center[0] + dx * scale, playerRuntime.position.y, region.center[2] + dz * scale];
        }

        if (playerRuntime.position.y < -2) {
            next = region.spawn;
        }

        if (!next) {
            return;
        }

        playerRuntime.position.set(next[0], next[1], next[2]);
        playerRuntime.velocity.set(0, 0, 0);
        setDreamPlayerPosition(next);
        const collider = physicsWorld.getCollider(playerRuntime.colliderHandle);
        const body = collider?.parent();
        body?.setTranslation({ x: next[0], y: next[1], z: next[2] }, true);
    });

    return null;
}
