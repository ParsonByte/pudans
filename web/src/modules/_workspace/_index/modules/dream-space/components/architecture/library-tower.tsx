"use client";

import { CuboidCollider, RigidBody } from "@react-three/rapier";

import type { StructurePlan } from "../../types";

export function LibraryTower({ plan }: { plan: StructurePlan }) {
    return (
        <RigidBody colliders={false} position={plan.position} rotation={[0, plan.rotation, 0]} type="fixed">
            <CuboidCollider args={[2.3 * plan.scale[0], 4 * plan.scale[1], 2.1 * plan.scale[2]]} position={[0, 4, 0]} />
            {[0, 1, 2, 3].map((level) => (
                <mesh key={level} position={[level * 0.18 - 0.3, 1.4 + level * 1.8, 0]} scale={plan.scale}>
                    <boxGeometry args={[4.3 - level * 0.45, 1.55, 3.7 - level * 0.25]} />
                    <meshStandardMaterial
                        color="#11162d"
                        emissive={plan.accent}
                        emissiveIntensity={0.035 + level * 0.012}
                        metalness={0.35}
                        roughness={0.45}
                    />
                </mesh>
            ))}
            <mesh position={[0, 8.1, 0]}>
                <cylinderGeometry args={[0.22, 0.3, 2.8, 8]} />
                <meshBasicMaterial color={plan.accent} toneMapped={false} />
            </mesh>
            <pointLight color={plan.accent} distance={15} intensity={3} position={[0, 6, 0]} />
        </RigidBody>
    );
}
