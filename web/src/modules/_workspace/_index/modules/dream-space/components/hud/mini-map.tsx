"use client";

import { enterDreamRegion } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { regionById } from "../../world/world-generator";

export function MiniMap() {
    const world = useDreamStore((state) => state.world);
    const activeRegionId = useDreamStore((state) => state.activeRegionId);
    const playerPosition = useDreamStore((state) => state.playerPose.position);

    if (!world) {
        return null;
    }

    const scale = 0.64;

    return (
        <section aria-label="世界小地图" className={`${styles.glassPanel} ${styles.miniMap}`}>
            <div className={styles.miniMapCanvas}>
                <svg role="img" viewBox="-100 -84 200 168">
                    <title>六个梦境天地的相对位置</title>
                    {world.regions.map((region) => (
                        <g key={region.id}>
                            <circle
                                aria-label={`前往${region.name}`}
                                className={`${styles.mapRegion} ${
                                    region.id === activeRegionId ? styles.mapRegionActive : ""
                                }`}
                                cx={region.center[0] * scale}
                                cy={region.center[2] * scale}
                                onClick={() => enterDreamRegion(region.id)}
                                onKeyDown={(event) => {
                                    if (event.key === "Enter" || event.key === " ") {
                                        event.preventDefault();
                                        enterDreamRegion(region.id);
                                    }
                                }}
                                r={region.radius * scale * 0.52}
                                role="button"
                                style={{ cursor: "pointer" }}
                                tabIndex={0}
                            />
                            <text
                                className={styles.mapLabel}
                                textAnchor="middle"
                                x={region.center[0] * scale}
                                y={region.center[2] * scale + 3}
                            >
                                {region.name}
                            </text>
                            {region.portals
                                .filter((portal) => portal.kind === "external")
                                .map((portal) => (
                                    <circle
                                        className={styles.mapPortal}
                                        cx={portal.position[0] * scale}
                                        cy={portal.position[2] * scale}
                                        key={portal.id}
                                        r={1.2}
                                    />
                                ))}
                        </g>
                    ))}
                    <circle
                        className={styles.mapPlayer}
                        cx={playerPosition[0] * scale}
                        cy={playerPosition[2] * scale}
                        r={1.8}
                    />
                </svg>
            </div>
            <div className={styles.miniMapFooter}>
                <span>{regionById(world, activeRegionId).name}</span>
                <span>M 关闭地图 · 点击区域传送</span>
            </div>
        </section>
    );
}
