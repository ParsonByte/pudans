"use client";

import { useDreamStore } from "../../stores/use-dream-store";
import type { MemoryLayout } from "../../types";
import { InteractableGlow } from "./interactable-glow";

export function MemoryPickup({ memory }: { memory: MemoryLayout }) {
    const discovered = useDreamStore((state) => state.discoveredMemoryIds.includes(memory.id));

    if (discovered) {
        return null;
    }

    return <InteractableGlow accent={memory.accent} position={memory.groundPoint} />;
}
