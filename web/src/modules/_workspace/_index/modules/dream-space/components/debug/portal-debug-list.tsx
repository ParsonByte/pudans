"use client";

import { useMemo } from "react";

import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";

export function PortalDebugList() {
    const world = useDreamStore((state) => state.world);
    const portals = useMemo(() => world?.regions.flatMap((region) => region.portals) ?? [], [world]);

    return (
        <div className={styles.debugGrid}>
            {portals.slice(0, 8).map((portal) => (
                <div className={styles.debugItem} key={portal.id}>
                    <span>{portal.kind}</span>
                    <span>{portal.id}</span>
                </div>
            ))}
        </div>
    );
}
