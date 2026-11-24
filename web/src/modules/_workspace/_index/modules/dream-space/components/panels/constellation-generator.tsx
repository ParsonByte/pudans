"use client";

import { useMemo } from "react";

import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";

export function ConstellationGenerator({ memoryIds }: { memoryIds: string[] }) {
    const world = useDreamStore((state) => state.world);
    const stars = useMemo(() => {
        const source = world?.memories.filter((memory) => memoryIds.includes(memory.id)) ?? [];
        const width = 280;
        const height = 100;

        return source.map((memory, index) => ({
            id: memory.id,
            x: 22 + ((index * 61 + memory.title.length * 7) % (width - 44)),
            y: 20 + ((index * 37 + memory.tags.length * 11) % (height - 40)),
        }));
    }, [memoryIds, world]);

    return (
        <div className={styles.constellationPreview}>
            <svg aria-hidden className={styles.constellationSvg} viewBox="0 0 280 100">
                {stars.slice(1).map((star, index) => (
                    <line
                        key={`line-${star.id}`}
                        stroke="rgba(164, 150, 255, 0.34)"
                        strokeWidth="0.55"
                        x1={stars[index].x}
                        x2={star.x}
                        y1={stars[index].y}
                        y2={star.y}
                    />
                ))}
                {stars.map((star, index) => (
                    <g key={star.id}>
                        <circle
                            cx={star.x}
                            cy={star.y}
                            fill={index === 0 ? "#FFC857" : "#A89AFF"}
                            opacity={index === 0 ? 0.95 : 0.68}
                            r={index === 0 ? 2.4 : 1.5}
                        />
                        <circle cx={star.x} cy={star.y} fill="none" r="5" stroke="rgba(255,255,255,0.08)" />
                    </g>
                ))}
            </svg>
        </div>
    );
}
