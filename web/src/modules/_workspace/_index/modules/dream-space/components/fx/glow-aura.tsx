"use client";

import { Billboard } from "@react-three/drei";
import * as THREE from "three";

import type { Vec3 } from "../../types";

export function GlowAura({ accent, position, scale = 1 }: { accent: string; position: Vec3; scale?: number }) {
    return (
        <Billboard position={position}>
            <mesh scale={scale}>
                <circleGeometry args={[0.85, 40]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={accent}
                    depthWrite={false}
                    opacity={0.13}
                    transparent
                />
            </mesh>
        </Billboard>
    );
}
