"use client";

import { useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { WaterPlan } from "../../types";

export function WaterRipple({ plan }: { plan: WaterPlan }) {
    const group = useRef<THREE.Group>(null);

    useFrame(({ clock }) => {
        group.current?.children.forEach((child, index) => {
            const phase = (clock.elapsedTime * 0.18 + index / 3) % 1;
            child.scale.setScalar(0.7 + phase * 1.8);

            if ((child as THREE.Mesh).material instanceof THREE.MeshBasicMaterial) {
                (child as THREE.Mesh<THREE.BufferGeometry, THREE.MeshBasicMaterial>).material.opacity =
                    (1 - phase) * 0.16;
            }
        });
    });

    return (
        <group position={[plan.position[0], 0.075, plan.position[2]]} ref={group}>
            {[0, 1, 2].map((index) => (
                <mesh key={index} rotation={[-Math.PI / 2, 0, 0]}>
                    <ringGeometry args={[1, 1.04, 56]} />
                    <meshBasicMaterial
                        blending={THREE.AdditiveBlending}
                        color={plan.accent}
                        depthWrite={false}
                        opacity={0.12}
                        transparent
                    />
                </mesh>
            ))}
        </group>
    );
}
