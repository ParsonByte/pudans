"use client";

import { MEMORY_TYPE_META } from "../../constants";
import styles from "../../styles/dream-space.module.css";
import type { MemoryLayout } from "../../types";
import { formatMemoryDate } from "../../world/memory-queries";

export function MemoryCard({ memory, onSelect }: { memory: MemoryLayout; onSelect: (memoryId: string) => void }) {
    const meta = MEMORY_TYPE_META[memory.type];

    return (
        <button
            className="flex w-full flex-col gap-1.5 rounded-[10px] border border-white/[0.07] bg-white/[0.025] p-2.5 text-left transition hover:border-violet-300/25 hover:bg-white/[0.05]"
            onClick={() => onSelect(memory.id)}
            type="button"
        >
            <span className="flex w-full items-center gap-2">
                <span className={styles.legendSwatch} style={{ background: meta.color, color: meta.color }} />
                <strong className="min-w-0 flex-1 truncate text-[11px] font-medium text-white/78">
                    {memory.title}
                </strong>
                <span className={styles.countBadge}>{meta.label}</span>
            </span>
            <span className="line-clamp-2 text-[9px] leading-relaxed text-white/36">{memory.summary}</span>
            <span className="flex items-center justify-between text-[8px] text-white/24">
                <span>{formatMemoryDate(memory.createdAt)}</span>
                <span>重要度 {Math.round(memory.importance * 100)}</span>
            </span>
        </button>
    );
}
