"use client";

import { useMemo } from "react";

import type { NaturePlan } from "../../types";
import { createSeededRandom, hashString } from "../../world/random";

export function FlowerField({ plan }: { plan: NaturePlan }) {
    const flowers = useMemo(() => {
        const random = createSeededRandom(hashString(plan.id));

        return Array.from({ length: 8 }, (_, index) => ({
            color: index % 3 === 0 ? "#FFC857" : plan.accent,
            height: 0.28 + random() * 0.5 * plan.scale,
            x: (random() - 0.5) * 2.6 * plan.scale,
            z: (random() - 0.5) * 2.6 * plan.scale,
        }));
    }, [plan]);

    return (
        <group position={plan.position}>
            {flowers.map((flower, index) => (
                <group key={index} position={[flower.x, flower.height, flower.z]}>
                    <mesh>
                        <sphereGeometry args={[0.075 * plan.scale, 8, 6]} />
                        <meshBasicMaterial color={flower.color} toneMapped={false} />
                    </mesh>
                    <mesh position={[0, -flower.height / 2, 0]}>
                        <cylinderGeometry args={[0.008, 0.012, flower.height, 3]} />
                        <meshBasicMaterial color={plan.accent} transparent opacity={0.32} />
                    </mesh>
                </group>
            ))}
        </group>
    );
}
