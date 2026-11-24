"use client";

import { Html } from "@react-three/drei";

import styles from "../../styles/dream-space.module.css";
import type { MemoryLayout } from "../../types";

export function GroundProjection({ memory }: { memory: MemoryLayout }) {
    return (
        <Html
            center
            className={styles.groundProjection}
            distanceFactor={11}
            position={[memory.groundPoint[0], memory.groundPoint[1] + 0.08, memory.groundPoint[2]]}
        >
            <span className={styles.projectionKicker}>记忆投影</span>
            <strong>{memory.title}</strong>
            <span>{memory.tags.slice(0, 3).join(" · ")}</span>
        </Html>
    );
}
