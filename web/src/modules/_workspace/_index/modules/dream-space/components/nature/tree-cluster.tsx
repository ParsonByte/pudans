"use client";

import { useMemo } from "react";

import type { NaturePlan } from "../../types";
import { createSeededRandom, hashString } from "../../world/random";

export function TreeCluster({ plan }: { plan: NaturePlan }) {
    const trees = useMemo(() => {
        const random = createSeededRandom(hashString(plan.id));

        return Array.from({ length: 3 }, (_, index) => ({
            height: plan.scale * (2.4 + random() * 1.8),
            x: (index - 1) * plan.scale * 0.9 + random() * 0.3,
            z: (random() - 0.5) * plan.scale * 1.5,
        }));
    }, [plan]);

    return (
        <group position={plan.position}>
            {trees.map((tree, index) => (
                <group key={index} position={[tree.x, 0, tree.z]}>
                    <mesh position={[0, tree.height * 0.34, 0]}>
                        <cylinderGeometry args={[0.09 * plan.scale, 0.15 * plan.scale, tree.height * 0.68, 6]} />
                        <meshStandardMaterial color="#171b30" roughness={0.9} />
                    </mesh>
                    <mesh position={[0, tree.height * 0.78, 0]}>
                        <coneGeometry args={[0.9 * plan.scale, tree.height * 0.72, 7]} />
                        <meshStandardMaterial
                            color={plan.accent}
                            emissive={plan.accent}
                            emissiveIntensity={0.1}
                            roughness={0.8}
                        />
                    </mesh>
                </group>
            ))}
        </group>
    );
}
