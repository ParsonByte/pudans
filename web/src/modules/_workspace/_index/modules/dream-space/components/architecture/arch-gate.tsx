"use client";

import * as THREE from "three";

import type { StructurePlan } from "../../types";

export function ArchGate({ plan }: { plan: StructurePlan }) {
    return (
        <group position={plan.position} rotation={[0, plan.rotation, 0]} scale={plan.scale}>
            {[-1.55, 1.55].map((x) => (
                <mesh key={x} position={[x, 2.2, 0]}>
                    <boxGeometry args={[0.42, 4.4, 0.5]} />
                    <meshStandardMaterial color="#11172c" metalness={0.4} roughness={0.42} />
                </mesh>
            ))}
            <mesh position={[0, 4.5, 0]}>
                <torusGeometry args={[1.72, 0.14, 12, 40, Math.PI]} />
                <meshBasicMaterial color={plan.accent} toneMapped={false} />
            </mesh>
            <mesh position={[0, 2.1, 0]}>
                <planeGeometry args={[2.7, 4.1]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={plan.accent}
                    depthWrite={false}
                    opacity={0.055}
                    side={THREE.DoubleSide}
                    transparent
                />
            </mesh>
        </group>
    );
}
