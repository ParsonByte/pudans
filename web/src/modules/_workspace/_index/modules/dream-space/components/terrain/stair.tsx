"use client";

import { CuboidCollider, RigidBody } from "@react-three/rapier";

import type { Vec3 } from "../../types";

export function Stair({ accent, position }: { accent: string; position: Vec3 }) {
    return (
        <RigidBody colliders={false} position={position} rotation={[0, -0.2, 0]} type="fixed">
            {[0, 1, 2, 3, 4].map((step) => {
                const width = 2.2 - step * 0.08;
                const stepPosition: Vec3 = [0, step * 0.18, step * 0.48];

                return (
                    <group key={step}>
                        <CuboidCollider args={[width / 2, 0.08, 0.24]} position={stepPosition} />
                        <mesh position={stepPosition}>
                            <boxGeometry args={[width, 0.16, 0.48]} />
                            <meshStandardMaterial
                                color="#141a36"
                                emissive={accent}
                                emissiveIntensity={0.04 + step * 0.012}
                                metalness={0.35}
                                roughness={0.58}
                            />
                        </mesh>
                    </group>
                );
            })}
        </RigidBody>
    );
}
