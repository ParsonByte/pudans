"use client";

import { Compass, Sparkles } from "lucide-react";

import { useDreamStore } from "../../stores/use-dream-store";

export function PlayerInventory() {
    const discovered = useDreamStore((state) => state.discoveredMemoryIds.length);
    const visited = useDreamStore((state) => state.visitedRegionIds.length);

    return (
        <div className="flex items-center gap-3 text-xs text-white/64">
            <span className="inline-flex items-center gap-1.5">
                <Sparkles aria-hidden size={13} />
                {discovered} 枚记忆
            </span>
            <span className="inline-flex items-center gap-1.5">
                <Compass aria-hidden size={13} />
                {visited} 个天地
            </span>
        </div>
    );
}
