"use client";

import * as THREE from "three";

import type { NaturePlan } from "../../types";

export function CrystalFormation({ plan }: { plan: NaturePlan }) {
    return (
        <group position={plan.position}>
            {[0, 1, 2, 3].map((index) => (
                <mesh
                    key={index}
                    position={[(index - 1.5) * 0.34, (0.5 + (index % 2) * 0.25) * plan.scale, Math.sin(index) * 0.3]}
                    rotation={[0, index * 0.55, (index - 1.5) * 0.12]}
                >
                    <octahedronGeometry args={[0.24 * plan.scale, 0]} />
                    <meshStandardMaterial
                        color={plan.accent}
                        emissive={plan.accent}
                        emissiveIntensity={0.7}
                        metalness={0.12}
                        roughness={0.16}
                        toneMapped={false}
                    />
                </mesh>
            ))}
            <pointLight color={plan.accent} distance={7} intensity={1.1} position={[0, 1, 0]} />
            <mesh position={[0, 0.04, 0]} rotation={[-Math.PI / 2, 0, 0]}>
                <ringGeometry args={[0.8, 0.88, 36]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={plan.accent}
                    depthWrite={false}
                    opacity={0.2}
                    transparent
                />
            </mesh>
        </group>
    );
}
