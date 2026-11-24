"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { RegionDefinition } from "../../types";
import { createSeededRandom } from "../../world/random";

export function WindField({ region, reducedMotion }: { region: RegionDefinition; reducedMotion: boolean }) {
    const group = useRef<THREE.Group>(null);
    const streaks = useMemo(() => {
        const random = createSeededRandom(region.terrain.seed ^ 0x991d);

        return Array.from({ length: 9 }, (_, index) => ({
            position: [
                region.center[0] + (random() - 0.5) * 34,
                region.center[1] + 3 + random() * 8,
                region.center[2] + (random() - 0.5) * 34,
            ] as [number, number, number],
            scale: 3 + random() * 5 + index * 0.2,
        }));
    }, [region]);

    useFrame(({ clock }) => {
        if (!group.current || reducedMotion) {
            return;
        }

        group.current.children.forEach((child, index) => {
            child.position.x = streaks[index].position[0] + Math.sin(clock.elapsedTime * 0.25 + index) * 2.2;
            child.scale.x = streaks[index].scale * (0.8 + Math.sin(clock.elapsedTime * 0.2 + index) * 0.2);
        });
    });

    return (
        <group ref={group}>
            {streaks.map((streak, index) => (
                <mesh key={`${streak.scale}-${index}`} position={streak.position} rotation={[0, 0.2, 0.08]}>
                    <planeGeometry args={[1, 0.012]} />
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
