"use client";

import { useMemo } from "react";

import type { NaturePlan } from "../../types";
import { createSeededRandom, hashString } from "../../world/random";

export function VineCurtain({ plan }: { plan: NaturePlan }) {
    const vines = useMemo(() => {
        const random = createSeededRandom(hashString(plan.id));

        return Array.from({ length: 7 }, (_, index) => ({
            height: 1.4 + random() * 2.8,
            x: (index - 3) * 0.23 * plan.scale,
            z: (random() - 0.5) * 0.38,
        }));
    }, [plan]);

    return (
        <group position={[plan.position[0], plan.position[1] + 3.5, plan.position[2]]}>
            {vines.map((vine, index) => (
                <mesh key={index} position={[vine.x, -vine.height / 2, vine.z]}>
                    <planeGeometry args={[0.018, vine.height]} />
                    <meshBasicMaterial color={plan.accent} transparent opacity={0.38} side={2} />
                </mesh>
            ))}
        </group>
    );
}
