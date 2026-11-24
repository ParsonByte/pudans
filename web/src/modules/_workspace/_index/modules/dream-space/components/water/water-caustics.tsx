"use client";

import * as THREE from "three";

import type { WaterPlan } from "../../types";

export function WaterCaustics({ plan }: { plan: WaterPlan }) {
    return (
        <group position={[plan.position[0], 0.065, plan.position[2]]} rotation={[-Math.PI / 2, 0, 0]}>
            {[0, 1, 2].map((index) => (
                <mesh key={index} rotation={[0, 0, index * 0.42]} scale={1 + index * 0.22}>
                    <ringGeometry args={[1.45, 1.48, 6]} />
                    <meshBasicMaterial
                        blending={THREE.AdditiveBlending}
                        color={plan.accent}
                        depthWrite={false}
                        opacity={0.12 - index * 0.025}
                        transparent
                    />
                </mesh>
            ))}
        </group>
    );
}
