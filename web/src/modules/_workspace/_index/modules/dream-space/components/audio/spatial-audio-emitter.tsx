"use client";

import { useEffect } from "react";

import { useDreamStore } from "../../stores/use-dream-store";
import { playDreamCue } from "../../world/audio-runtime";

export function SpatialAudioEmitter() {
    const portals = useDreamStore(
        (state) => state.world?.regions.find((region) => region.id === state.activeRegionId)?.portals,
    );
    const playerPosition = useDreamStore((state) => state.playerPose.position);

    useEffect(() => {
        const nearest = portals
            ?.map((portal) => ({
                distance: Math.hypot(portal.position[0] - playerPosition[0], portal.position[2] - playerPosition[2]),
                portal,
            }))
            .sort((left, right) => left.distance - right.distance)[0];

        if (nearest && nearest.distance < 2.6) {
            playDreamCue("portal", 0.35);
        }
    }, [playerPosition, portals]);

    return null;
}
