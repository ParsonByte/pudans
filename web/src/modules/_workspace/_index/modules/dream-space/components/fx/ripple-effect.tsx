"use client";

import { useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { Vec3 } from "../../types";

export function RippleEffect({ accent, position }: { accent: string; position: Vec3 }) {
    const group = useRef<THREE.Group>(null);

    useFrame(({ clock }) => {
        group.current?.children.forEach((child, index) => {
            const phase = (clock.elapsedTime * 0.22 + index / 3) % 1;
            child.scale.setScalar(0.4 + phase * 2.8);

            if ((child as THREE.Mesh<THREE.BufferGeometry, THREE.MeshBasicMaterial>).material) {
                (child as THREE.Mesh<THREE.BufferGeometry, THREE.MeshBasicMaterial>).material.opacity =
                    (1 - phase) * 0.24;
            }
        });
    });

    return (
        <group position={[position[0], position[1] + 0.08, position[2]]} ref={group}>
            {[0, 1, 2].map((index) => (
                <mesh key={index} rotation={[-Math.PI / 2, 0, 0]}>
                    <ringGeometry args={[0.9, 0.95, 64]} />
                    <meshBasicMaterial
                        blending={THREE.AdditiveBlending}
                        color={accent}
                        depthWrite={false}
                        opacity={0.18}
                        transparent
                    />
                </mesh>
            ))}
        </group>
    );
}
