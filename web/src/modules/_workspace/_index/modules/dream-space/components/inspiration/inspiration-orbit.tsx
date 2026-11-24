"use client";

import { useMemo } from "react";

import styles from "../../styles/dream-space.module.css";

export function InspirationOrbit({ accent, count }: { accent: string; count: number }) {
    const nodes = useMemo(
        () =>
            Array.from({ length: Math.min(8, Math.max(3, count)) }, (_, index) => {
                const angle = (index / Math.min(8, Math.max(3, count))) * Math.PI * 2;

                return {
                    x: 50 + Math.cos(angle) * 34,
                    y: 50 + Math.sin(angle) * 27,
                };
            }),
        [count],
    );

    return (
        <svg aria-hidden className={styles.constellationPreview} viewBox="0 0 100 100">
            <circle cx="50" cy="50" fill="none" r="35" stroke={`${accent}38`} strokeDasharray="2 4" />
            <circle cx="50" cy="50" fill={accent} opacity="0.72" r="3" />
            {nodes.map((node, index) => (
                <g key={`${node.x}-${node.y}`}>
                    <line stroke={`${accent}4a`} x1="50" x2={node.x} y1="50" y2={node.y} />
                    <circle cx={node.x} cy={node.y} fill={index % 2 === 0 ? accent : "#FFC857"} r="1.6" />
                </g>
            ))}
        </svg>
    );
}
