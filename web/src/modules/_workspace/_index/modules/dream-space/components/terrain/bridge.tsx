"use client";

import { CuboidCollider, RigidBody } from "@react-three/rapier";
import * as THREE from "three";

import type { Vec3 } from "../../types";

export function Bridge({ accent, position, scale = 1 }: { accent: string; position: Vec3; scale?: number }) {
    return (
        <RigidBody colliders={false} position={[position[0], 1, position[2]]} type="fixed">
            <group scale={scale}>
                <CuboidCollider args={[3.7, 0.08, 0.8]} />
                <mesh rotation={[0, 0, -0.1]}>
                    <boxGeometry args={[7.4, 0.16, 1.6]} />
                    <meshStandardMaterial color="#131735" metalness={0.55} roughness={0.28} />
                </mesh>
                {[-3, 0, 3].map((offset) => (
                    <mesh key={offset} position={[offset, 0.12, 0.76]}>
                        <boxGeometry args={[0.08, 0.55, 0.08]} />
                        <meshBasicMaterial color={accent} toneMapped={false} />
                    </mesh>
                ))}
                <mesh position={[0, 0.025, 0]} rotation={[-Math.PI / 2, 0, 0]}>
                    <planeGeometry args={[7.1, 1.25]} />
                    <meshBasicMaterial
                        blending={THREE.AdditiveBlending}
                        color={accent}
                        depthWrite={false}
                        opacity={0.2}
                        transparent
                    />
                </mesh>
            </group>
        </RigidBody>
    );
}
