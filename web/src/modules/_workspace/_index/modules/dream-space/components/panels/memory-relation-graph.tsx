"use client";

import styles from "../../styles/dream-space.module.css";
import type { MemoryLayout } from "../../types";

export function MemoryRelationGraph({ memory, memories }: { memory: MemoryLayout; memories: MemoryLayout[] }) {
    const related = memory.relations
        .map((id) => memories.find((candidate) => candidate.id === id))
        .filter((candidate) => candidate !== undefined)
        .slice(0, 5);
    const points = related.map((relation, index) => {
        const angle = (index / Math.max(1, related.length)) * Math.PI * 2 - Math.PI / 2;

        return {
            x: 120 + Math.cos(angle) * 66,
            y: 72 + Math.sin(angle) * 46,
            memory: relation,
        };
    });

    return (
        <svg aria-label={`${memory.title}的记忆关系`} className={styles.relationGraph} viewBox="0 0 240 144">
            {points.map((point) => (
                <line
                    key={`line-${point.memory.id}`}
                    stroke="rgba(152, 139, 255, 0.28)"
                    strokeDasharray="2 3"
                    x1="120"
                    x2={point.x}
                    y1="72"
                    y2={point.y}
                />
            ))}
            <circle cx="120" cy="72" fill="rgba(109, 93, 252, 0.28)" r="12" stroke={memory.accent} />
            <text fill="rgba(255,255,255,0.72)" fontSize="6" textAnchor="middle" x="120" y="88">
                当前记忆
            </text>
            {points.map((point) => (
                <g key={point.memory.id}>
                    <circle cx={point.x} cy={point.y} fill={point.memory.accent} opacity="0.72" r="5" />
                    <text fill="rgba(255,255,255,0.52)" fontSize="5.2" textAnchor="middle" x={point.x} y={point.y + 13}>
                        {point.memory.title.slice(0, 8)}
                    </text>
                </g>
            ))}
        </svg>
    );
}
