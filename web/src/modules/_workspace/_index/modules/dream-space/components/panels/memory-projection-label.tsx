"use client";

import styles from "../../styles/dream-space.module.css";
import type { MemoryLayout } from "../../types";

export function MemoryProjectionLabel({ memory }: { memory: MemoryLayout }) {
    return (
        <span className={styles.groundProjection}>
            <span className={styles.projectionKicker}>MEMORY PROJECTED</span>
            <strong>{memory.title}</strong>
            <span>{memory.tags.slice(0, 2).join(" · ")}</span>
        </span>
    );
}
