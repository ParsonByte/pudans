"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { RegionDefinition } from "../../types";
import { createSeededRandom } from "../../world/random";

export function AuroraCurtain({ region, reducedMotion }: { region: RegionDefinition; reducedMotion: boolean }) {
    const group = useRef<THREE.Group>(null);
    const curtains = useMemo(() => {
        const random = createSeededRandom(region.terrain.seed ^ 0xa0a0a);

        return Array.from({ length: 5 }, (_, index) => ({
            color: index % 2 === 0 ? region.theme.accent : region.theme.secondary,
            position: [
                region.center[0] - 46 + index * 23,
                region.center[1] + 31 + random() * 12,
                region.center[2] - 62 - index * 5,
            ] as [number, number, number],
            rotation: [0.2, -0.3 + index * 0.14, -0.1 + random() * 0.2] as [number, number, number],
            scale: [18 + random() * 11, 30 + random() * 17, 1] as [number, number, number],
        }));
    }, [region]);

    useFrame(({ clock }) => {
        if (!group.current || reducedMotion) {
            return;
        }

        group.current.children.forEach((child, index) => {
            child.rotation.z = Math.sin(clock.elapsedTime * 0.06 + index) * 0.045;
        });
    });

    return (
        <group ref={group}>
            {curtains.map((curtain, index) => (
                <mesh
                    key={`${curtain.color}-${index}`}
                    position={curtain.position}
                    rotation={curtain.rotation}
                    scale={curtain.scale}
                >
                    <planeGeometry args={[1, 1, 18, 1]} />
                    <meshBasicMaterial
                        blending={THREE.AdditiveBlending}
                        color={curtain.color}
                        depthWrite={false}
                        opacity={0.04}
                        side={THREE.DoubleSide}
                        transparent
                    />
                </mesh>
            ))}
        </group>
    );
}
