"use client";

import * as THREE from "three";

import type { Vec3 } from "../../types";

export function GroundDecal({ accent, position, scale = 2.5 }: { accent: string; position: Vec3; scale?: number }) {
    return (
        <mesh position={[position[0], 0.055, position[2]]} rotation={[-Math.PI / 2, 0, 0]}>
            <circleGeometry args={[scale, 48]} />
            <meshBasicMaterial
                blending={THREE.AdditiveBlending}
                color={accent}
                depthWrite={false}
                opacity={0.055}
                transparent
            />
        </mesh>
    );
}
