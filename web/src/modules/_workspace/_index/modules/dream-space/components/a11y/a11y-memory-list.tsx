"use client";

import { useMemo } from "react";

import { selectDreamMemory } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { filterMemories } from "../../world/memory-queries";
import { A11yPortalList } from "./a11y-portal-list";

export function A11yMemoryList() {
    const world = useDreamStore((state) => state.world);
    const filters = useDreamStore((state) => state.filters);
    const memories = useMemo(() => (world ? filterMemories(world.memories, filters) : []), [filters, world]);

    return (
        <section aria-label="幻境空间记忆与门户替代列表" className="sr-only">
            <h2>记忆星辰列表</h2>
            <ul>
                {memories.map((memory) => (
                    <li key={memory.id}>
                        <button onClick={() => selectDreamMemory(memory.id)} type="button">
                            {memory.title}，{memory.tags.join("、")}，重要度 {Math.round(memory.importance * 100)}
                        </button>
                    </li>
                ))}
            </ul>
            <A11yPortalList />
            <p className={styles.narrative}>按 Tab 打开记忆筛选，按 Esc 关闭面板并暂停。</p>
        </section>
    );
}
