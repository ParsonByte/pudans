"use client";

import * as THREE from "three";

import type { StructurePlan } from "../../types";

export function MemoryMonument({ plan }: { plan: StructurePlan }) {
    return (
        <group position={plan.position} rotation={[0, plan.rotation, 0]} scale={plan.scale}>
            <mesh position={[0, 0.25, 0]}>
                <cylinderGeometry args={[2.2, 2.6, 0.5, 6]} />
                <meshStandardMaterial color="#121831" metalness={0.4} roughness={0.48} />
            </mesh>
            <mesh position={[0, 3.3, 0]}>
                <coneGeometry args={[1.15, 5.8, 4]} />
                <meshStandardMaterial
                    color="#151b36"
                    emissive={plan.accent}
                    emissiveIntensity={0.14}
                    metalness={0.32}
                    roughness={0.44}
                />
            </mesh>
            <mesh position={[0, 4.1, 0]}>
                <octahedronGeometry args={[0.52, 0]} />
                <meshBasicMaterial color={plan.accent} toneMapped={false} />
            </mesh>
            <mesh position={[0, 0.045, 0]} rotation={[-Math.PI / 2, 0, 0]}>
                <ringGeometry args={[2.5, 2.72, 64]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={plan.accent}
                    depthWrite={false}
                    opacity={0.3}
                    transparent
                />
            </mesh>
        </group>
    );
}
