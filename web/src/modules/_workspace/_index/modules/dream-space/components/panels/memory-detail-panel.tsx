"use client";

import { useMemo } from "react";

import { Sparkles, X } from "lucide-react";

import { MEMORY_EMOTION_META, MEMORY_TYPE_META } from "../../constants";
import { selectDreamMemory, setDreamInspiration } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import type { MemoryLayout } from "../../types";
import { createInspiration, formatMemoryDate, formatMemoryTime } from "../../world/memory-queries";
import { HudPanel } from "../hud/hud-panel";
import { MemoryProjectionLabel } from "./memory-projection-label";
import { MemoryRelationGraph } from "./memory-relation-graph";
import { MemoryTimeline } from "./memory-timeline";

function RelationRow({ memory, onSelect }: { memory: MemoryLayout; onSelect: (id: string) => void }) {
    return (
        <button className={styles.relationItem} onClick={() => onSelect(memory.id)} type="button">
            <span className="flex min-w-0 items-center gap-2">
                <span className={styles.legendSwatch} style={{ background: memory.accent }} />
                <span className="truncate">{memory.title}</span>
            </span>
            <span className={styles.countBadge}>{MEMORY_TYPE_META[memory.type].label}</span>
        </button>
    );
}

export function MemoryDetailPanel() {
    const world = useDreamStore((state) => state.world);
    const selectedMemoryId = useDreamStore((state) => state.selectedMemoryId);
    const inspirationRound = useDreamStore((state) => state.inspirationRound);
    const memory = useMemo(
        () => world?.memories.find((candidate) => candidate.id === selectedMemoryId),
        [selectedMemoryId, world],
    );
    const related = useMemo(
        () =>
            world && memory
                ? memory.relations
                      .map((id) => world.memories.find((candidate) => candidate.id === id))
                      .filter((candidate) => candidate !== undefined)
                : [],
        [memory, world],
    );

    if (!memory || !world) {
        return null;
    }

    const typeMeta = MEMORY_TYPE_META[memory.type];
    const emotionMeta = MEMORY_EMOTION_META[memory.emotion];

    return (
        <HudPanel
            className={styles.widePanel}
            kicker={`${typeMeta.label.toUpperCase()} MEMORY`}
            onClose={() => selectDreamMemory(null)}
            title="记忆详情"
        >
            <div className={styles.memoryMeta}>
                <span>{formatMemoryDate(memory.createdAt)}</span>
                <span>{formatMemoryTime(memory.createdAt)}</span>
                <span>{emotionMeta.label}</span>
                <span>重要度 {Math.round(memory.importance * 100)}</span>
            </div>
            <h3 className={`${styles.memoryTitle} mt-2`}>{memory.title}</h3>
            <p className={styles.memorySummary}>{memory.summary}</p>
            <MemoryProjectionLabel memory={memory} />
            <div className={`${styles.memoryTags} mt-3`}>
                {memory.tags.map((tag) => (
                    <span className={`${styles.tag} ${styles.tagStatic}`} key={tag}>
                        #{tag}
                    </span>
                ))}
            </div>
            <div className={styles.memoryRelations}>
                <span className={styles.filterLabel}>关联记忆</span>
                {related.length > 0 ? (
                    related
                        .slice(0, 4)
                        .map((relation) => (
                            <RelationRow key={relation.id} memory={relation} onSelect={selectDreamMemory} />
                        ))
                ) : (
                    <p className={styles.emptyState}>这条记忆暂时独自漂浮。</p>
                )}
            </div>
            <MemoryRelationGraph memory={memory} memories={world.memories} />
            <div className="mt-3">
                <span className={styles.filterLabel}>同一时间层</span>
                <MemoryTimeline memories={world.memories.filter((item) => item.regionId === memory.regionId)} />
            </div>
            <div className="mt-3 flex gap-2">
                <button
                    className={styles.primaryButton}
                    onClick={() =>
                        setDreamInspiration(createInspiration(world.memories, memory.id, Date.now() + inspirationRound))
                    }
                    type="button"
                >
                    <Sparkles aria-hidden size={13} />
                    由这条记忆生成灵感
                </button>
                <button
                    aria-label="关闭详情"
                    className={styles.ghostButton}
                    onClick={() => selectDreamMemory(null)}
                    type="button"
                >
                    <X aria-hidden size={13} />
                </button>
            </div>
        </HudPanel>
    );
}
