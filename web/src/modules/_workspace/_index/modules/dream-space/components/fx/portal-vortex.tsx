"use client";

import { useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

export function PortalVortex({ accent, reducedMotion }: { accent: string; reducedMotion: boolean }) {
    const group = useRef<THREE.Group>(null);

    useFrame(({ clock }) => {
        if (!group.current || reducedMotion) {
            return;
        }

        group.current.rotation.z = clock.elapsedTime * 0.28;
        group.current.children.forEach((child, index) => {
            child.rotation.z = -clock.elapsedTime * (0.18 + index * 0.04);
        });
    });

    return (
        <group ref={group}>
            {[0, 1, 2, 3].map((index) => (
                <mesh key={index} rotation={[0, 0, index * 0.8]} scale={1 + index * 0.16}>
                    <torusGeometry args={[1.08, 0.012 + index * 0.006, 6, 72, Math.PI * 1.45]} />
                    <meshBasicMaterial
                        blending={THREE.AdditiveBlending}
                        color={accent}
                        depthWrite={false}
                        opacity={0.42 - index * 0.07}
                        transparent
                    />
                </mesh>
            ))}
            <mesh>
                <circleGeometry args={[1.25, 64]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={accent}
                    depthWrite={false}
                    opacity={0.06}
                    side={THREE.DoubleSide}
                    transparent
                />
            </mesh>
        </group>
    );
}
