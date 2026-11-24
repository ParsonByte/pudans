"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { Vec3 } from "../../types";
import { createSeededRandom } from "../../world/random";

export function ShatterEffect({ accent, position, seed }: { accent: string; position: Vec3; seed: number }) {
    const group = useRef<THREE.Group>(null);
    const fragments = useMemo(() => {
        const random = createSeededRandom(seed);

        return Array.from({ length: 12 }, () => ({
            direction: new THREE.Vector3(random() - 0.5, random() * 0.7, random() - 0.5).normalize(),
            size: 0.03 + random() * 0.08,
        }));
    }, [seed]);

    useFrame(({ clock }) => {
        if (!group.current) {
            return;
        }

        group.current.children.forEach((child, index) => {
            const phase = (clock.elapsedTime * 0.35 + index * 0.08) % 1;
            child.position.copy(fragments[index].direction).multiplyScalar(phase * 3);
            child.scale.setScalar(fragments[index].size * (1 - phase));
        });
    });

    return (
        <group position={position} ref={group}>
            {fragments.map((fragment, index) => (
                <mesh key={index}>
                    <tetrahedronGeometry args={[fragment.size, 0]} />
                    <meshBasicMaterial color={accent} toneMapped={false} transparent opacity={0.6} />
                </mesh>
            ))}
        </group>
    );
}
