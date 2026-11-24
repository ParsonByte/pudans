"use client";

import { Billboard } from "@react-three/drei";
import * as THREE from "three";

import type { Vec3 } from "../../types";

export function QuestMarker({ accent, position }: { accent: string; position: Vec3 }) {
    return (
        <Billboard position={[position[0], position[1] + 3.4, position[2]]}>
            <mesh>
                <octahedronGeometry args={[0.18, 0]} />
                <meshBasicMaterial color={accent} toneMapped={false} />
            </mesh>
            <mesh scale={1.8}>
                <ringGeometry args={[0.3, 0.32, 32]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={accent}
                    depthWrite={false}
                    opacity={0.3}
                    transparent
                />
            </mesh>
        </Billboard>
    );
}
