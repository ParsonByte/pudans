"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { RegionDefinition } from "../../types";
import { createSeededRandom } from "../../world/random";

export function CloudLayer({ region, reducedMotion }: { region: RegionDefinition; reducedMotion: boolean }) {
    const group = useRef<THREE.Group>(null);
    const clouds = useMemo(() => {
        const random = createSeededRandom(region.terrain.seed ^ 0xc10d);

        return Array.from({ length: 8 }, (_, index) => ({
            position: [
                region.center[0] + (random() - 0.5) * 95,
                region.center[1] + 25 + random() * 15,
                region.center[2] + (random() - 0.5) * 80,
            ] as [number, number, number],
            scale: [7 + random() * 10, 2.8 + random() * 4, 1] as [number, number, number],
            speed: 0.15 + index * 0.02,
        }));
    }, [region]);

    useFrame(({ clock }) => {
        if (!group.current || reducedMotion) {
            return;
        }

        group.current.children.forEach((child, index) => {
            child.position.x = clouds[index].position[0] + Math.sin(clock.elapsedTime * clouds[index].speed) * 4;
        });
    });

    return (
        <group ref={group}>
            {clouds.map((cloud, index) => (
                <mesh key={`${cloud.speed}-${index}`} position={cloud.position} scale={cloud.scale}>
                    <sphereGeometry args={[1, 18, 10]} />
                    <meshBasicMaterial color={region.theme.secondary} depthWrite={false} opacity={0.035} transparent />
                </mesh>
            ))}
        </group>
    );
}
