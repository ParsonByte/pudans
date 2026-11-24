"use client";

import { useRef } from "react";

import { useFrame } from "@react-three/fiber";

import { setDreamInteractionTarget } from "../../stores/dream-store";
import type { InteractionTarget, MemoryLayout, RegionDefinition } from "../../types";
import { playerRuntime } from "../../world/player-runtime";

export function ProximityPrompt({ memories, region }: { memories: MemoryLayout[]; region: RegionDefinition }) {
    const lastUpdate = useRef(0);

    useFrame(({ clock }) => {
        if (clock.elapsedTime - lastUpdate.current < 0.12) {
            return;
        }

        lastUpdate.current = clock.elapsedTime;
        let nearest: InteractionTarget | null = null;
        let nearestDistance = Number.POSITIVE_INFINITY;

        region.portals.forEach((portal) => {
            const distance = Math.hypot(
                playerRuntime.position.x - portal.position[0],
                playerRuntime.position.z - portal.position[2],
            );

            if (distance < 4 && distance < nearestDistance) {
                nearest = {
                    id: portal.id,
                    kind: "portal",
                    label: portal.label,
                    position: portal.position,
                    subtitle: portal.description,
                };
                nearestDistance = distance;
            }
        });

        memories.forEach((memory) => {
            const distance = Math.hypot(
                playerRuntime.position.x - memory.groundPoint[0],
                playerRuntime.position.z - memory.groundPoint[2],
            );

            if (distance < 4.4 && distance < nearestDistance) {
                nearest = {
                    id: memory.id,
                    kind: "memory",
                    label: memory.title,
                    position: memory.groundPoint,
                    subtitle: "阅读这条记忆在地面的投影",
                };
                nearestDistance = distance;
            }
        });

        setDreamInteractionTarget(nearest);
    });

    return null;
}
