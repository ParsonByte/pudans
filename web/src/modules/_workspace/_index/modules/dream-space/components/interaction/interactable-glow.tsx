"use client";

import { useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { Vec3 } from "../../types";

export function InteractableGlow({ accent, position }: { accent: string; position: Vec3 }) {
    const mesh = useRef<THREE.Mesh>(null);

    useFrame(({ clock }) => {
        if (mesh.current) {
            mesh.current.rotation.z = clock.elapsedTime * 0.34;
            mesh.current.scale.setScalar(1 + Math.sin(clock.elapsedTime * 1.4) * 0.08);
        }
    });

    return (
        <mesh position={[position[0], position[1] + 0.06, position[2]]} rotation={[-Math.PI / 2, 0, 0]} ref={mesh}>
            <ringGeometry args={[0.8, 0.86, 48]} />
            <meshBasicMaterial
                blending={THREE.AdditiveBlending}
                color={accent}
                depthWrite={false}
                opacity={0.24}
                transparent
            />
        </mesh>
    );
}
