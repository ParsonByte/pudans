"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { NaturePlan } from "../../types";
import { createSeededRandom, hashString } from "../../world/random";

export function ButterflyFlock({ plan }: { plan: NaturePlan }) {
    const group = useRef<THREE.Group>(null);
    const wings = useMemo(() => {
        const random = createSeededRandom(hashString(plan.id));

        return Array.from({ length: 6 }, (_, index) => ({
            offset: random() * 6,
            position: [(random() - 0.5) * 3.4, 1.2 + random() * 2, (random() - 0.5) * 3.4] as [number, number, number],
            size: 0.1 + index * 0.018,
        }));
    }, [plan]);

    useFrame(({ clock }) => {
        if (!group.current) {
            return;
        }

        group.current.children.forEach((child, index) => {
            child.position.y =
                wings[index].position[1] + Math.sin(clock.elapsedTime * 0.8 + wings[index].offset) * 0.27;
            child.rotation.z = Math.sin(clock.elapsedTime * 2.6 + index) * 0.36;
        });
    });

    return (
        <group position={[plan.position[0], plan.position[1], plan.position[2]]} ref={group}>
            {wings.map((wing, index) => (
                <mesh key={index} position={wing.position} rotation={[0, index, 0]}>
                    <planeGeometry args={[wing.size * 2.4, wing.size]} />
                    <meshBasicMaterial color={plan.accent} side={THREE.DoubleSide} transparent opacity={0.72} />
                </mesh>
            ))}
        </group>
    );
}
