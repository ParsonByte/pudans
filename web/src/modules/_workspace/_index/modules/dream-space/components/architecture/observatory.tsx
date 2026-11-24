"use client";

import { CuboidCollider, RigidBody } from "@react-three/rapier";

import type { StructurePlan } from "../../types";

export function Observatory({ plan }: { plan: StructurePlan }) {
    return (
        <RigidBody colliders={false} position={plan.position} rotation={[0, plan.rotation, 0]} type="fixed">
            <CuboidCollider args={[2.4 * plan.scale[0], 1.6, 2.4 * plan.scale[2]]} position={[0, 1.6, 0]} />
            <mesh position={[0, 0.7, 0]} scale={plan.scale}>
                <cylinderGeometry args={[2.5, 2.9, 1.4, 8]} />
                <meshStandardMaterial color="#0e1530" metalness={0.5} roughness={0.38} />
            </mesh>
            <mesh position={[0, 2.15, 0]} scale={plan.scale}>
                <sphereGeometry args={[2.4, 20, 12, 0, Math.PI * 2, 0, Math.PI / 2]} />
                <meshStandardMaterial
                    color="#172142"
                    emissive={plan.accent}
                    emissiveIntensity={0.1}
                    metalness={0.45}
                    roughness={0.32}
                />
            </mesh>
            <mesh position={[0, 3.2, -0.45]} rotation={[0.5, 0, 0]} scale={plan.scale}>
                <cylinderGeometry args={[0.45, 0.75, 3.6, 12]} />
                <meshStandardMaterial color={plan.accent} emissive={plan.accent} emissiveIntensity={0.45} />
            </mesh>
        </RigidBody>
    );
}
