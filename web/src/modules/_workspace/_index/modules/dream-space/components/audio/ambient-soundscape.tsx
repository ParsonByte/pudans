"use client";

import { useEffect } from "react";

import { useDreamStore } from "../../stores/use-dream-store";

export function AmbientSoundscape() {
    const enabled = useDreamStore((state) => state.audioEnabled);
    const weather = useDreamStore(
        (state) => state.world?.regions.find((region) => region.id === state.activeRegionId)?.weather,
    );

    useEffect(() => {
        document.documentElement.dataset.dreamAmbience = enabled ? (weather ?? "clear") : "muted";
    }, [enabled, weather]);

    return null;
}
