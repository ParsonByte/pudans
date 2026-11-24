"use client";

import { MEMORY_EMOTION_META } from "../../constants";
import { toggleDreamEmotion } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";

export function EmotionLegend() {
    const selectedEmotions = useDreamStore((state) => state.filters.emotions);

    return (
        <div className={styles.filterOptions}>
            {Object.entries(MEMORY_EMOTION_META).map(([emotion, meta]) => {
                const active = selectedEmotions.includes(emotion as keyof typeof MEMORY_EMOTION_META);

                return (
                    <button
                        aria-pressed={active}
                        className={`${styles.pill} ${active ? styles.pillActive : ""}`}
                        key={emotion}
                        onClick={() => toggleDreamEmotion(emotion as keyof typeof MEMORY_EMOTION_META)}
                        type="button"
                    >
                        <span className={styles.legendSwatch} style={{ background: meta.color, color: meta.color }} />
                        {meta.label}
                    </button>
                );
            })}
        </div>
    );
}
