"use client";

import { useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { RegionDefinition } from "../../types";

export function FogVolume({ region, reducedMotion }: { region: RegionDefinition; reducedMotion: boolean }) {
    const group = useRef<THREE.Group>(null);

    useFrame(({ clock }) => {
        if (group.current && !reducedMotion) {
            group.current.rotation.y = clock.elapsedTime * 0.006;
        }
    });

    return (
        <group position={region.center} ref={group}>
            {[0, 1, 2, 3].map((index) => (
                <mesh
                    key={index}
                    position={[Math.cos(index * 1.7) * 12, 0.5 + index * 0.8, Math.sin(index * 1.7) * 12]}
                    rotation={[-Math.PI / 2, 0, index]}
                >
                    <planeGeometry args={[26 - index * 3, 15 + index * 2]} />
                    <meshBasicMaterial
                        blending={THREE.AdditiveBlending}
                        color={region.theme.secondary}
                        depthWrite={false}
                        opacity={0.018}
                        side={THREE.DoubleSide}
                        transparent
                    />
                </mesh>
            ))}
        </group>
    );
}
