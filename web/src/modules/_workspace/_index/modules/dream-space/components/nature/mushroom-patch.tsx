"use client";

import type { NaturePlan } from "../../types";

export function MushroomPatch({ plan }: { plan: NaturePlan }) {
    return (
        <group position={plan.position}>
            {[0, 1, 2].map((index) => (
                <group
                    key={index}
                    position={[(index - 1) * 0.48 * plan.scale, 0, Math.sin(index * 2.3) * 0.45 * plan.scale]}
                >
                    <mesh position={[0, 0.24 * plan.scale, 0]}>
                        <cylinderGeometry args={[0.055, 0.08, 0.48, 5]} />
                        <meshStandardMaterial color="#d7cee8" roughness={0.8} />
                    </mesh>
                    <mesh position={[0, 0.48 * plan.scale, 0]} scale={[1, 0.45, 1]}>
                        <sphereGeometry args={[0.24 * plan.scale, 10, 7]} />
                        <meshBasicMaterial color={plan.accent} toneMapped={false} />
                    </mesh>
                </group>
            ))}
        </group>
    );
}
