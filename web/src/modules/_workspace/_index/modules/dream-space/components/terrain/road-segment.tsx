"use client";

import * as THREE from "three";

import type { Vec3 } from "../../types";

export function RoadSegment({
    accent,
    length,
    position,
    rotation = 0,
    width = 1.4,
}: {
    accent: string;
    length: number;
    position: Vec3;
    rotation?: number;
    width?: number;
}) {
    return (
        <mesh position={[position[0], position[1] + 0.03, position[2]]} rotation={[-Math.PI / 2, 0, rotation]}>
            <planeGeometry args={[width, length]} />
            <meshBasicMaterial
                blending={THREE.AdditiveBlending}
                color={accent}
                depthWrite={false}
                opacity={0.16}
                transparent
            />
        </mesh>
    );
}
