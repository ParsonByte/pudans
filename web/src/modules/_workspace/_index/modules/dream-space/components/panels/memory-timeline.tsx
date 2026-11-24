"use client";

import styles from "../../styles/dream-space.module.css";
import type { MemoryLayout } from "../../types";
import { formatMemoryDate } from "../../world/memory-queries";

export function MemoryTimeline({ memories }: { memories: MemoryLayout[] }) {
    return (
        <div className={styles.timelineList}>
            {memories
                .slice()
                .sort((left, right) => right.timestamp - left.timestamp)
                .slice(0, 8)
                .map((memory) => (
                    <div className={styles.timelineItem} key={memory.id}>
                        <span className={styles.legendSwatch} style={{ background: memory.accent }} />
                        <span className="min-w-0 flex-1 truncate">{memory.title}</span>
                        <span className="shrink-0 text-white/24">{formatMemoryDate(memory.createdAt)}</span>
                    </div>
                ))}
        </div>
    );
}
