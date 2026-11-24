"use client";

import styles from "../../styles/dream-space.module.css";
import type { RegionDefinition } from "../../types";

export function LoreFragment({ region }: { region: RegionDefinition }) {
    const fragment = `在${region.name}，${region.ambientText} 建造者以${region.theme.accent}标记每一道门的边缘，提醒旅人：梦境不是逃离现实，而是让记忆找到可以落地的形状。`;

    return (
        <article className={styles.loreCard}>
            <span className={styles.projectionKicker}>FRAGMENT · {region.subtitle}</span>
            <p className={styles.narrative}>{fragment}</p>
        </article>
    );
}
