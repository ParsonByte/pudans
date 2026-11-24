"use client";

import * as THREE from "three";

import type { WaterPlan } from "../../types";

export function RiverStream({ plan }: { plan: WaterPlan }) {
    return (
        <group position={plan.position}>
            {[0, 1, 2].map((index) => (
                <mesh
                    key={index}
                    position={[0, 0.04 + index * 0.004, index * 2.1 - 2.1]}
                    rotation={[-Math.PI / 2, 0, -0.16]}
                >
                    <planeGeometry args={[1.8 - index * 0.2, 2.4]} />
                    <meshBasicMaterial
                        blending={THREE.AdditiveBlending}
                        color={plan.accent}
                        depthWrite={false}
                        opacity={0.11}
                        transparent
                    />
                </mesh>
            ))}
        </group>
    );
}
