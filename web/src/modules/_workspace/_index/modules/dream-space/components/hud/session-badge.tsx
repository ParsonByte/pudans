"use client";

import { Radio } from "lucide-react";

import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";

export function SessionBadge() {
    const sessionId = useDreamStore((state) => state.world?.sessionId);

    return (
        <span className={styles.sessionBadge} title={sessionId}>
            <span className={styles.statusDot} />
            <Radio aria-hidden size={9} />
            {sessionId?.slice(0, 8) ?? "SESSION"}
        </span>
    );
}
