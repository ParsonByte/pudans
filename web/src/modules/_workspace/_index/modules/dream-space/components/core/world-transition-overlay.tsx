"use client";

import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";

export function WorldTransitionOverlay() {
    const phase = useDreamStore((state) => state.phase);
    const label = useDreamStore((state) => state.transitionLabel);

    if (phase !== "transition") {
        return null;
    }

    return (
        <div aria-live="polite" className={styles.worldTransition}>
            <span className={styles.transitionRing} />
            <span className={styles.transitionRing} />
            <strong>{label}</strong>
            <small>THE DREAM IS REFOLDING</small>
        </div>
    );
}
