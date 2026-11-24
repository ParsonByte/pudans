"use client";

import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { regionById } from "../../world/world-generator";

export function SessionStoryRecap() {
    const world = useDreamStore((state) => state.world);
    const activeRegionId = useDreamStore((state) => state.activeRegionId);
    const visited = useDreamStore((state) => state.visitedRegionIds);
    const discovered = useDreamStore((state) => state.discoveredMemoryIds.length);
    const openedPortals = useDreamStore((state) => state.openedPortalIds.length);

    if (!world) {
        return null;
    }

    return (
        <div className={styles.subPanel}>
            <span className={styles.filterLabel}>本会话叙事</span>
            <p className={styles.narrative}>
                你从{regionById(world, "awakening").name}醒来，抵达了{regionById(world, activeRegionId).name}， 穿过{" "}
                {visited.length - 1} 道区域边界，收集 {discovered} 条记忆，并让 {openedPortals} 道门户短暂发光。
            </p>
        </div>
    );
}
