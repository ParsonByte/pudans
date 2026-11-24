"use client";

import { Billboard } from "@react-three/drei";
import * as THREE from "three";

import type { MemoryLayout } from "../../types";

export function HoverHighlight({ memory }: { memory: MemoryLayout }) {
    return (
        <Billboard position={memory.position}>
            <mesh>
                <ringGeometry args={[0.5, 0.54, 48]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={memory.accent}
                    depthWrite={false}
                    opacity={0.66}
                    transparent
                />
            </mesh>
        </Billboard>
    );
}
