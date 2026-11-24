"use client";

import { RotateCcw } from "lucide-react";

import { MEMORY_TYPE_META } from "../../constants";
import {
    resetDreamFilters,
    setDreamImportance,
    setDreamResultOnly,
    toggleDreamPanel,
    toggleDreamType,
} from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { filterMemories } from "../../world/memory-queries";
import { HudPanel } from "../hud/hud-panel";
import { EmotionLegend } from "./emotion-legend";
import { MemorySearch } from "./memory-search";
import { TagCloud } from "./tag-cloud";

export function MemoryFilterPanel() {
    const filters = useDreamStore((state) => state.filters);
    const world = useDreamStore((state) => state.world);
    const resultOnly = useDreamStore((state) => state.resultOnly);
    const resultCount = world ? filterMemories(world.memories, filters).length : 0;

    return (
        <HudPanel
            className={styles.widePanel}
            kicker="MEMORY FILTERS"
            onClose={() => toggleDreamPanel("filters")}
            title="记忆筛选"
        >
            <MemorySearch />
            <div className={styles.filterGroup}>
                <span className={styles.filterLabel}>
                    类型
                    <span className={styles.countBadge}>{resultCount}</span>
                </span>
                <div className={styles.filterOptions}>
                    {Object.entries(MEMORY_TYPE_META).map(([type, meta]) => {
                        const active = filters.types.includes(type as keyof typeof MEMORY_TYPE_META);

                        return (
                            <button
                                aria-pressed={active}
                                className={`${styles.pill} ${active ? styles.pillActive : ""}`}
                                key={type}
                                onClick={() => toggleDreamType(type as keyof typeof MEMORY_TYPE_META)}
                                type="button"
                            >
                                {meta.label}
                            </button>
                        );
                    })}
                </div>
            </div>
            <div className={styles.filterGroup}>
                <span className={styles.filterLabel}>情绪</span>
                <EmotionLegend />
            </div>
            <div className={styles.filterGroup}>
                <span className={styles.filterLabel}>标签</span>
                <TagCloud />
            </div>
            <div className={styles.filterGroup}>
                <label className={styles.filterLabel} htmlFor="dream-importance">
                    最低重要度
                    <span>{Math.round(filters.minImportance * 100)}%</span>
                </label>
                <input
                    className={styles.rangeInput}
                    id="dream-importance"
                    max={1}
                    min={0}
                    onChange={(event) => setDreamImportance(Number(event.target.value))}
                    step={0.05}
                    type="range"
                    value={filters.minImportance}
                />
            </div>
            <label className={styles.checkboxRow}>
                仅保留命中结果
                <input
                    checked={resultOnly}
                    onChange={(event) => setDreamResultOnly(event.target.checked)}
                    type="checkbox"
                />
            </label>
            <button className={`${styles.ghostButton} mt-3 w-full`} onClick={resetDreamFilters} type="button">
                <RotateCcw aria-hidden size={12} />
                重置全部筛选
            </button>
        </HudPanel>
    );
}
