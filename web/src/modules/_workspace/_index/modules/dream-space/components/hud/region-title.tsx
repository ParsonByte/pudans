"use client";

import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { regionById } from "../../world/world-generator";

export function RegionTitle() {
    const world = useDreamStore((state) => state.world);
    const activeRegionId = useDreamStore((state) => state.activeRegionId);
    const region = world ? regionById(world, activeRegionId) : undefined;

    if (!region) {
        return null;
    }

    return (
        <div className={styles.titleStack}>
            <div className={styles.breadcrumb}>
                <span>DREAM WORLD</span>
                <span>/</span>
                <strong>{region.subtitle}</strong>
            </div>
            <h1 className={styles.title}>{region.name}</h1>
            <p className={styles.subtitle}>{region.ambientText}</p>
        </div>
    );
}
