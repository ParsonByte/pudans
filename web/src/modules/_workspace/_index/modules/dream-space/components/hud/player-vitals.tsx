"use client";

import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";

export function PlayerVitals() {
    const stamina = useDreamStore((state) => state.playerPose.stamina);
    const grounded = useDreamStore((state) => state.playerPose.grounded);

    return (
        <div className={styles.playerVitals}>
            <span className={styles.panelKicker}>{grounded ? "DREAM WALKER" : "AIRBORNE"}</span>
            <div className={styles.staminaTrack}>
                <div className={styles.staminaBar} style={{ width: `${stamina * 100}%` }} />
            </div>
            <span className={styles.timelineRange}>{Math.round(stamina * 100)}% 体力</span>
        </div>
    );
}
