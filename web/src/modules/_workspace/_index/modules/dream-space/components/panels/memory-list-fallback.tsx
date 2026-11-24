"use client";

import { selectDreamMemory } from "../../stores/dream-store";
import styles from "../../styles/dream-space.module.css";
import type { MemoryLayout } from "../../types";
import { MemoryCard } from "./memory-card";

export function MemoryListFallback({ memories }: { memories: MemoryLayout[] }) {
    if (memories.length === 0) {
        return <div className={styles.emptyState}>当前筛选下没有记忆星辰。</div>;
    }

    return (
        <div className={styles.portalRegistry}>
            {memories.map((memory) => (
                <MemoryCard key={memory.id} memory={memory} onSelect={selectDreamMemory} />
            ))}
        </div>
    );
}
