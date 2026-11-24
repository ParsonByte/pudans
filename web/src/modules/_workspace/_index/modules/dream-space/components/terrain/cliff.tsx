"use client";

import { CuboidCollider, RigidBody } from "@react-three/rapier";

import type { Vec3 } from "../../types";

export function Cliff({ accent, position }: { accent: string; position: Vec3 }) {
    return (
        <RigidBody colliders={false} position={position} type="fixed">
            <CuboidCollider args={[2.4, 1.5, 1.8]} position={[0, 1.3, 0]} />
            <mesh position={[0, 1.3, 0]}>
                <dodecahedronGeometry args={[2.6, 0]} />
                <meshStandardMaterial
                    color="#11152a"
                    emissive={accent}
                    emissiveIntensity={0.05}
                    metalness={0.12}
                    roughness={0.9}
                />
            </mesh>
        </RigidBody>
    );
}
