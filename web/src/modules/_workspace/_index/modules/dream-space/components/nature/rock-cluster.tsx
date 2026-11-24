"use client";

import type { NaturePlan } from "../../types";

export function RockCluster({ plan }: { plan: NaturePlan }) {
    return (
        <group position={plan.position} rotation={[0, plan.variant * 0.4, 0]}>
            {[0, 1, 2].map((index) => (
                <mesh
                    key={index}
                    position={[(index - 1) * 0.65 * plan.scale, 0.28 * plan.scale, Math.sin(index) * 0.45]}
                    scale={[1, 0.6 + index * 0.15, 1]}
                >
                    <dodecahedronGeometry args={[0.48 * plan.scale, 0]} />
                    <meshStandardMaterial color="#14182b" metalness={0.13} roughness={0.9} />
                </mesh>
            ))}
        </group>
    );
}
