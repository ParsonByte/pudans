"use client";

import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";

export function SeedDebugPanel() {
    const world = useDreamStore((state) => state.world);

    if (!world) {
        return null;
    }

    return (
        <div className={styles.debugGrid}>
            <div className={styles.debugItem}>
                <span>seed</span>
                <span>{world.seed}</span>
            </div>
            <div className={styles.debugItem}>
                <span>short</span>
                <span>{world.shortCode}</span>
            </div>
            <div className={styles.debugItem}>
                <span>session</span>
                <span>{world.sessionId.slice(0, 12)}</span>
            </div>
            <div className={styles.debugItem}>
                <span>user</span>
                <span>{world.userId}</span>
            </div>
        </div>
    );
}
