"use client";

import * as THREE from "three";

import type { WaterPlan } from "../../types";

export function Fountain({ plan }: { plan: WaterPlan }) {
    return (
        <group position={plan.position} scale={plan.scale}>
            <mesh position={[0, 0.28, 0]}>
                <cylinderGeometry args={[1.1, 1.35, 0.5, 18]} />
                <meshStandardMaterial color="#11162b" metalness={0.5} roughness={0.4} />
            </mesh>
            <mesh position={[0, 0.56, 0]} rotation={[-Math.PI / 2, 0, 0]}>
                <circleGeometry args={[0.94, 32]} />
                <meshBasicMaterial color={plan.accent} transparent opacity={0.24} />
            </mesh>
            {[0, 1, 2].map((index) => (
                <mesh
                    key={index}
                    position={[Math.cos(index * 2.1) * 0.3, 1.15 + index * 0.1, Math.sin(index * 2.1) * 0.3]}
                >
                    <octahedronGeometry args={[0.08, 0]} />
                    <meshBasicMaterial color={plan.accent} transparent opacity={0.7} />
                </mesh>
            ))}
            <mesh position={[0, 0.04, 0]} rotation={[-Math.PI / 2, 0, 0]}>
                <ringGeometry args={[1.3, 1.44, 48]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={plan.accent}
                    depthWrite={false}
                    opacity={0.22}
                    transparent
                />
            </mesh>
        </group>
    );
}
