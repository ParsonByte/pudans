"use client";

import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";

export function Compass() {
    const yaw = useDreamStore((state) => state.playerPose.yaw);
    const degrees = Math.round(((((-yaw * 180) / Math.PI) % 360) + 360) % 360);

    return (
        <div aria-label={`朝向 ${degrees} 度`} className={styles.compass} role="img">
            <span className={styles.compassLabel}>N</span>
            <span className={styles.compassRose} style={{ transform: `rotate(${(-yaw * 180) / Math.PI}deg)` }} />
            <span className={styles.compassDegrees}>{degrees}°</span>
        </div>
    );
}
