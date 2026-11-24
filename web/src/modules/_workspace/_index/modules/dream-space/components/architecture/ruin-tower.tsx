"use client";

import type { StructurePlan } from "../../types";

export function RuinTower({ plan }: { plan: StructurePlan }) {
    return (
        <group position={plan.position} rotation={[0, plan.rotation, 0]}>
            {[0, 1, 2].map((level) => (
                <mesh
                    key={level}
                    position={[Math.sin(level * 1.9) * 0.45, 1.2 + level * 2.3, Math.cos(level * 1.7) * 0.3]}
                    rotation={[level * 0.13, level * 0.7, level * 0.17]}
                >
                    <cylinderGeometry args={[1.3 - level * 0.16, 1.7 - level * 0.16, 2.1, 6, 1, false, level]} />
                    <meshStandardMaterial
                        color="#18141b"
                        emissive={plan.accent}
                        emissiveIntensity={0.08}
                        metalness={0.22}
                        roughness={0.78}
                    />
                </mesh>
            ))}
            <mesh position={[0, 0.2, 0]} rotation={[-Math.PI / 2, 0, 0]}>
                <ringGeometry args={[2, 2.12, 48]} />
                <meshBasicMaterial color={plan.accent} transparent opacity={0.28} />
            </mesh>
        </group>
    );
}
