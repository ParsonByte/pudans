"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { RegionDefinition } from "../../types";
import { createSeededRandom } from "../../world/random";

export function CreatureFlock({ region }: { region: RegionDefinition }) {
    const group = useRef<THREE.Group>(null);
    const creatures = useMemo(() => {
        const random = createSeededRandom(region.terrain.seed ^ 0xc0e0);

        return Array.from({ length: 7 }, (_, index) => ({
            offset: random() * Math.PI * 2,
            radius: 10 + random() * 16,
            y: 3 + random() * 8 + index * 0.2,
        }));
    }, [region]);

    useFrame(({ clock }) => {
        if (!group.current) {
            return;
        }

        group.current.children.forEach((child, index) => {
            const creature = creatures[index];
            const angle = clock.elapsedTime * 0.08 + creature.offset;
            child.position.set(
                region.center[0] + Math.cos(angle) * creature.radius,
                creature.y + Math.sin(clock.elapsedTime * 0.6 + index) * 0.22,
                region.center[2] + Math.sin(angle) * creature.radius,
            );
        });
    });

    return (
        <group ref={group}>
            {creatures.map((creature, index) => (
                <mesh key={index} rotation={[0, index, 0]}>
                    <planeGeometry args={[0.16, 0.06]} />
                    <meshBasicMaterial color={region.theme.star} transparent opacity={0.58} side={THREE.DoubleSide} />
                </mesh>
            ))}
        </group>
    );
}
