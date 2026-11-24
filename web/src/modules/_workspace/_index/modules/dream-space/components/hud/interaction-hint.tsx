"use client";

import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";

export function InteractionHint() {
    const target = useDreamStore((state) => state.interactionTarget);

    if (!target) {
        return null;
    }

    return (
        <div aria-live="polite" className={styles.interactionHint}>
            <span className={styles.keycap}>E</span>
            <span>{target.kind === "portal" ? "查看门户" : target.kind === "memory" ? "阅读记忆" : "交互"}</span>
            <strong className="text-white/80">{target.label}</strong>
        </div>
    );
}
