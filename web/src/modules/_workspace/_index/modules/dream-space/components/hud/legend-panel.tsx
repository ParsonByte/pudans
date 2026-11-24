"use client";

import { MEMORY_EMOTION_META, MEMORY_TYPE_META, WEATHER_LABELS } from "../../constants";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { regionById } from "../../world/world-generator";
import { HudPanel } from "./hud-panel";

export function LegendPanel() {
    const world = useDreamStore((state) => state.world);
    const activeRegionId = useDreamStore((state) => state.activeRegionId);
    const region = world ? regionById(world, activeRegionId) : undefined;

    return (
        <HudPanel kicker="DREAM LEGEND" title="星图图例">
            <div className={styles.legendList}>
                {Object.entries(MEMORY_TYPE_META).map(([type, meta]) => (
                    <div className={styles.legendRow} key={type}>
                        <span className={styles.legendSwatch} style={{ background: meta.color, color: meta.color }} />
                        <span>{meta.label}</span>
                        <span className="ml-auto opacity-50">{meta.shape}</span>
                    </div>
                ))}
                <div className="my-1 h-px bg-white/7" />
                {Object.entries(MEMORY_EMOTION_META)
                    .slice(0, 3)
                    .map(([emotion, meta]) => (
                        <div className={styles.legendRow} key={emotion}>
                            <span
                                className={styles.legendSwatch}
                                style={{ background: meta.color, color: meta.color }}
                            />
                            {meta.label}
                        </div>
                    ))}
                {region ? (
                    <div className={styles.legendRow}>
                        <span className={styles.statusDot} />
                        当前天气 · {WEATHER_LABELS[region.weather]}
                    </div>
                ) : null}
            </div>
        </HudPanel>
    );
}
