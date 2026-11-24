"use client";

import { useMemo } from "react";

import { setDreamDirectoryOpen } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { filterMemories } from "../../world/memory-queries";
import { HudPanel } from "../hud/hud-panel";
import { MemoryListFallback } from "./memory-list-fallback";
import { MemorySearch } from "./memory-search";

export function MemoryDirectoryPanel() {
    const world = useDreamStore((state) => state.world);
    const filters = useDreamStore((state) => state.filters);
    const matching = useMemo(() => (world ? filterMemories(world.memories, filters) : []), [filters, world]);

    return (
        <div className={styles.hudRight}>
            <HudPanel
                className={styles.widePanel}
                kicker={`${matching.length} STARS VISIBLE`}
                onClose={() => setDreamDirectoryOpen(false)}
                title="记忆目录"
            >
                <MemorySearch />
                <div className="mt-3">
                    <MemoryListFallback memories={matching} />
                </div>
            </HudPanel>
        </div>
    );
}
