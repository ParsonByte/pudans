"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { NaturePlan } from "../../types";
import { createSeededRandom, hashString } from "../../world/random";

export function FloatingPollen({ plan }: { plan: NaturePlan }) {
    const points = useRef<THREE.Points>(null);
    const positions = useMemo(() => {
        const random = createSeededRandom(hashString(plan.id));
        const data = new Float32Array(36 * 3);

        for (let index = 0; index < 36; index += 1) {
            data[index * 3] = plan.position[0] + (random() - 0.5) * 5;
            data[index * 3 + 1] = plan.position[1] + 0.4 + random() * 3.5;
            data[index * 3 + 2] = plan.position[2] + (random() - 0.5) * 5;
        }

        return data;
    }, [plan]);

    useFrame(({ clock }) => {
        if (points.current) {
            points.current.rotation.y = clock.elapsedTime * 0.04;
        }
    });

    return (
        <points ref={points}>
            <bufferGeometry>
                <bufferAttribute args={[positions, 3]} attach="attributes-position" />
            </bufferGeometry>
            <pointsMaterial
                blending={THREE.AdditiveBlending}
                color={plan.accent}
                depthWrite={false}
                opacity={0.7}
                size={0.055}
                transparent
            />
        </points>
    );
}
