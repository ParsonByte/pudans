"use client";

import styles from "../../styles/dream-space.module.css";
import type { RegionDefinition } from "../../types";

export function RegionStory({ region }: { region: RegionDefinition }) {
    return (
        <section className={styles.subPanel}>
            <span className={styles.filterLabel}>区域叙事</span>
            <p className={styles.narrative}>
                {region.name}以{region.weather === "clear" ? "静止的星光" : "缓慢移动的天气"}维持边界。
                {region.memoryIds.length} 条记忆漂浮在这里，等待旅人用脚步重新排列它们的关系。
            </p>
        </section>
    );
}
