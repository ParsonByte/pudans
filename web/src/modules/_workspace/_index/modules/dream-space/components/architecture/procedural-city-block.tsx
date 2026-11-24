"use client";

import type { StructurePlan } from "../../types";
import { createSeededRandom, hashString } from "../../world/random";

export function ProceduralCityBlock({ plan }: { plan: StructurePlan }) {
    const random = createSeededRandom(hashString(plan.id));
    const blocks = Array.from({ length: 5 }, (_, index) => ({
        height: 2.5 + random() * 5,
        x: (index % 3) * 2.6 - 2.6 + random() * 0.5,
        z: Math.floor(index / 3) * 2.7 - 1.3,
        width: 1.5 + random() * 1.4,
    }));

    return (
        <group position={plan.position} rotation={[0, plan.rotation, 0]} scale={plan.scale}>
            {blocks.map((block, index) => (
                <mesh key={index} position={[block.x, block.height / 2, block.z]}>
                    <boxGeometry args={[block.width, block.height, 1.4]} />
                    <meshStandardMaterial
                        color="#10152b"
                        emissive={plan.accent}
                        emissiveIntensity={index === 2 ? 0.15 : 0.04}
                        metalness={0.35}
                        roughness={0.54}
                    />
                </mesh>
            ))}
        </group>
    );
}
