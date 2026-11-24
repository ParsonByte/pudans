"use client";

import type { StructurePlan } from "../../types";

export function TempleShrine({ plan }: { plan: StructurePlan }) {
    return (
        <group position={plan.position} rotation={[0, plan.rotation, 0]} scale={plan.scale}>
            <mesh position={[0, 0.22, 0]}>
                <boxGeometry args={[4.5, 0.44, 3.8]} />
                <meshStandardMaterial color="#11162b" metalness={0.3} roughness={0.52} />
            </mesh>
            {[-1.4, 1.4].map((x) => (
                <mesh key={x} position={[x, 2, 0]}>
                    <boxGeometry args={[0.24, 3.4, 2.5]} />
                    <meshStandardMaterial color="#161d3a" metalness={0.3} roughness={0.48} />
                </mesh>
            ))}
            <mesh position={[0, 4, 0]} rotation={[0, 0, Math.PI / 4]}>
                <coneGeometry args={[2.75, 1.15, 4]} />
                <meshBasicMaterial color={plan.accent} toneMapped={false} transparent opacity={0.52} />
            </mesh>
        </group>
    );
}
