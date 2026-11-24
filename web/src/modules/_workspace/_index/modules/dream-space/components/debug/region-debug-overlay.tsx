"use client";

import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { regionById } from "../../world/world-generator";

export function RegionDebugOverlay() {
    const world = useDreamStore((state) => state.world);
    const activeRegionId = useDreamStore((state) => state.activeRegionId);
    const region = world ? regionById(world, activeRegionId) : undefined;

    if (!region) {
        return null;
    }

    return (
        <div className={styles.debugGrid}>
            <div className={styles.debugItem}>
                <span>region</span>
                <span>{region.id}</span>
            </div>
            <div className={styles.debugItem}>
                <span>center</span>
                <span>{region.center.join(", ")}</span>
            </div>
            <div className={styles.debugItem}>
                <span>radius</span>
                <span>{region.radius}</span>
            </div>
            <div className={styles.debugItem}>
                <span>weather</span>
                <span>{region.weather}</span>
            </div>
        </div>
    );
}
