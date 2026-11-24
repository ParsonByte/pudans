"use client";

import { CuboidCollider, RigidBody } from "@react-three/rapier";

import type { StructurePlan } from "../../types";

export function DreamBuilding({ plan }: { plan: StructurePlan }) {
    const height = 7 * plan.scale[1];

    return (
        <RigidBody colliders={false} position={plan.position} rotation={[0, plan.rotation, 0]} type="fixed">
            <CuboidCollider
                args={[2.1 * plan.scale[0], height / 2, 1.8 * plan.scale[2]]}
                position={[0, height / 2, 0]}
            />
            <mesh position={[0, height / 2, 0]} scale={[plan.scale[0], plan.scale[1], plan.scale[2]]}>
                <boxGeometry args={[4.1, 7, 3.6]} />
                <meshStandardMaterial color="#10152e" metalness={0.52} roughness={0.36} />
            </mesh>
            {[0.3, 0.46, 0.62, 0.78].map((ratio) => (
                <mesh key={ratio} position={[0, height * ratio, 1.82 * plan.scale[2]]}>
                    <boxGeometry args={[2.8 * plan.scale[0], 0.035, 0.025]} />
                    <meshBasicMaterial color={plan.accent} toneMapped={false} />
                </mesh>
            ))}
            <mesh position={[0, height + 0.4, 0]} rotation={[Math.PI / 2, 0, 0]}>
                <ringGeometry args={[0.55, 0.64, 32]} />
                <meshBasicMaterial color={plan.accent} toneMapped={false} transparent opacity={0.72} />
            </mesh>
        </RigidBody>
    );
}
