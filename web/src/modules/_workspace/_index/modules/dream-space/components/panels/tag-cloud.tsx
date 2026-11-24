"use client";

import { useMemo } from "react";

import { toggleDreamTag } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { collectMemoryTags } from "../../world/memory-queries";

export function TagCloud() {
    const world = useDreamStore((state) => state.world);
    const selectedTags = useDreamStore((state) => state.filters.tags);
    const tags = useMemo(() => (world ? collectMemoryTags(world.memories, 14) : []), [world]);

    return (
        <div className={styles.filterOptions}>
            {tags.map((tag) => {
                const active = selectedTags.includes(tag);

                return (
                    <button
                        aria-pressed={active}
                        className={`${styles.tag} ${active ? styles.tagActive : ""}`}
                        key={tag}
                        onClick={() => toggleDreamTag(tag)}
                        type="button"
                    >
                        #{tag}
                    </button>
                );
            })}
        </div>
    );
}
