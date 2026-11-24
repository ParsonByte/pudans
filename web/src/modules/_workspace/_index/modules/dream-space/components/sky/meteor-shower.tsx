"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { RegionDefinition } from "../../types";
import { createSeededRandom } from "../../world/random";

export function MeteorShower({ region, reducedMotion }: { region: RegionDefinition; reducedMotion: boolean }) {
    const group = useRef<THREE.Group>(null);
    const meteors = useMemo(() => {
        const random = createSeededRandom(region.terrain.seed ^ 0x3e7e0);

        return Array.from({ length: 4 }, (_, index) => ({
            offset: random() * Math.PI * 2,
            position: [
                region.center[0] - 80 + index * 42,
                region.center[1] + 38 + random() * 22,
                region.center[2] - 48 - random() * 55,
            ] as [number, number, number],
        }));
    }, [region]);

    useFrame(({ clock }) => {
        if (!group.current || reducedMotion) {
            return;
        }

        group.current.children.forEach((child, index) => {
            const phase = (clock.elapsedTime * 0.07 + index * 0.31 + meteors[index].offset) % 1;
            child.position.x = meteors[index].position[0] - phase * 26;
            child.position.y = meteors[index].position[1] - phase * 20;
            const material = (child as THREE.Mesh).material;

            if (material instanceof THREE.MeshBasicMaterial) {
                material.opacity = Math.sin(phase * Math.PI) * 0.36;
            }
        });
    });

    return (
        <group ref={group}>
            {meteors.map((meteor, index) => (
                <mesh key={`${meteor.offset}-${index}`} position={meteor.position} rotation={[0.6, 0.1, 0.74]}>
                    <planeGeometry args={[0.045, 5.4]} />
                    <meshBasicMaterial
                        blending={THREE.AdditiveBlending}
                        color={region.theme.star}
                        depthWrite={false}
                        opacity={0.16}
                        transparent
                    />
                </mesh>
            ))}
        </group>
    );
}
