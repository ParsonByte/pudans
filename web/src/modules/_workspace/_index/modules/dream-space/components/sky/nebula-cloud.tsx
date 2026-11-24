"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { RegionDefinition } from "../../types";
import { createSeededRandom } from "../../world/random";

export function NebulaCloud({ region, reducedMotion }: { region: RegionDefinition; reducedMotion: boolean }) {
    const group = useRef<THREE.Group>(null);
    const clouds = useMemo(() => {
        const random = createSeededRandom(region.terrain.seed ^ 0x91e1a);
        const palette = [region.theme.accent, region.theme.secondary, region.theme.star];

        return Array.from({ length: 7 }, (_, index) => {
            const angle = (index / 7) * Math.PI * 2 + random() * 0.6;
            const radius = 42 + random() * 75;

            return {
                color: palette[index % palette.length],
                position: [Math.cos(angle) * radius, 24 + random() * 38, Math.sin(angle) * radius] as [
                    number,
                    number,
                    number,
                ],
                scale: [22 + random() * 24, 8 + random() * 15, 1] as [number, number, number],
            };
        });
    }, [region]);

    useFrame(({ clock }) => {
        if (group.current && !reducedMotion) {
            group.current.rotation.y = clock.elapsedTime * 0.0028;
        }
    });

    return (
        <group position={region.center} ref={group}>
            {clouds.map((cloud, index) => (
                <mesh
                    key={`${cloud.color}-${index}`}
                    position={cloud.position}
                    rotation={[0, index * 0.7, index * 0.18]}
                    scale={cloud.scale}
                >
                    <planeGeometry args={[1, 1]} />
                    <meshBasicMaterial
                        blending={THREE.AdditiveBlending}
                        color={cloud.color}
                        depthWrite={false}
                        opacity={0.022}
                        side={THREE.DoubleSide}
                        toneMapped={false}
                        transparent
                    />
                </mesh>
            ))}
        </group>
    );
}
