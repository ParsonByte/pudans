"use client";

import * as THREE from "three";

import type { StructurePlan } from "../../types";

export function BridgePortal({ plan }: { plan: StructurePlan }) {
    return (
        <group position={plan.position} rotation={[0, plan.rotation, 0]} scale={plan.scale}>
            <mesh position={[0, 0.18, 0]}>
                <boxGeometry args={[8, 0.24, 2.2]} />
                <meshStandardMaterial color="#10152b" metalness={0.5} roughness={0.35} />
            </mesh>
            <mesh position={[0, 2.8, 0]}>
                <torusGeometry args={[2.8, 0.11, 8, 48, Math.PI]} />
                <meshBasicMaterial color={plan.accent} toneMapped={false} />
            </mesh>
            <mesh position={[0, 1.6, 0]}>
                <planeGeometry args={[4.6, 3.5]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={plan.accent}
                    depthWrite={false}
                    opacity={0.045}
                    side={THREE.DoubleSide}
                    transparent
                />
            </mesh>
        </group>
    );
}
