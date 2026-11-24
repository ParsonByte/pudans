"use client";

import { useMemo } from "react";

import type { NaturePlan } from "../../types";
import { createSeededRandom, hashString } from "../../world/random";

export function GlowingGrass({ plan }: { plan: NaturePlan }) {
    const blades = useMemo(() => {
        const random = createSeededRandom(hashString(plan.id));

        return Array.from({ length: 6 }, () => ({
            height: 0.3 + random() * 0.8 * plan.scale,
            x: (random() - 0.5) * 1.8 * plan.scale,
            z: (random() - 0.5) * 1.8 * plan.scale,
        }));
    }, [plan]);

    return (
        <group position={plan.position}>
            {blades.map((blade, index) => (
                <mesh key={index} position={[blade.x, blade.height / 2, blade.z]}>
                    <coneGeometry args={[0.025, blade.height, 4]} />
                    <meshBasicMaterial color={plan.accent} toneMapped={false} transparent opacity={0.55} />
                </mesh>
            ))}
        </group>
    );
}
