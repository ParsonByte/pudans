"use client";

import { useMemo } from "react";

import { useDreamStore } from "../../stores/use-dream-store";
import { regionById } from "../../world/world-generator";

export function ScreenReaderAnnouncer() {
    const world = useDreamStore((state) => state.world);
    const activeRegionId = useDreamStore((state) => state.activeRegionId);
    const selectedMemoryId = useDreamStore((state) => state.selectedMemoryId);
    const announcement = useMemo(() => {
        if (!world) {
            return "正在生成梦境世界。";
        }

        const region = regionById(world, activeRegionId);
        const selected = world.memories.find((memory) => memory.id === selectedMemoryId);

        return selected
            ? `已抵达${region.name}，当前选中记忆${selected.title}。`
            : `已抵达${region.name}。种子短码${world.shortCode}。`;
    }, [activeRegionId, selectedMemoryId, world]);

    return (
        <p aria-live="polite" className="sr-only">
            {announcement}
        </p>
    );
}
