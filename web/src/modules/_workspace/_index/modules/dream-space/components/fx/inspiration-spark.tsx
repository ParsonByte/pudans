"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { Vec3 } from "../../types";
import { createSeededRandom } from "../../world/random";

export function InspirationSpark({ accent, origin, seed }: { accent: string; origin: Vec3; seed: number }) {
    const points = useRef<THREE.Points>(null);
    const positions = useMemo(() => {
        const random = createSeededRandom(seed);
        const data = new Float32Array(40 * 3);

        for (let index = 0; index < 40; index += 1) {
            data[index * 3] = origin[0] + (random() - 0.5) * 16;
            data[index * 3 + 1] = origin[1] + 5 + random() * 12;
            data[index * 3 + 2] = origin[2] + (random() - 0.5) * 16;
        }

        return data;
    }, [origin, seed]);

    useFrame(({ clock }) => {
        if (points.current) {
            points.current.rotation.y = clock.elapsedTime * 0.032;
            points.current.position.y = Math.sin(clock.elapsedTime * 0.36) * 0.16;
        }
    });

    return (
        <points ref={points}>
            <bufferGeometry>
                <bufferAttribute args={[positions, 3]} attach="attributes-position" />
            </bufferGeometry>
            <pointsMaterial
                blending={THREE.AdditiveBlending}
                color={accent}
                depthWrite={false}
                opacity={0.68}
                size={0.08}
                transparent
            />
        </points>
    );
}
