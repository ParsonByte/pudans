"use client";

import { TIMELINE_END, TIMELINE_START } from "../../constants";
import { setDreamTimeRange } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { monthRangeLabel } from "../../world/memory-queries";

export function TimeSlider() {
    const filters = useDreamStore((state) => state.filters);
    const [start, end] = filters.timeRange;

    return (
        <section aria-label="记忆时间轴" className={styles.timeline}>
            <div className={styles.timelineHeader}>
                <span className={styles.panelKicker}>MEMORY TIMELINE</span>
                <span className={styles.timelineRange}>{monthRangeLabel(filters.timeRange)}</span>
            </div>
            <div className={styles.timelineInputs}>
                <input
                    aria-label="时间轴起点"
                    className={styles.rangeInput}
                    max={end}
                    min={TIMELINE_START}
                    onChange={(event) => setDreamTimeRange([Number(event.target.value), end])}
                    step={1}
                    type="range"
                    value={start}
                />
                <input
                    aria-label="时间轴终点"
                    className={styles.rangeInput}
                    max={TIMELINE_END}
                    min={start}
                    onChange={(event) => setDreamTimeRange([start, Number(event.target.value)])}
                    step={1}
                    type="range"
                    value={end}
                />
            </div>
        </section>
    );
}
