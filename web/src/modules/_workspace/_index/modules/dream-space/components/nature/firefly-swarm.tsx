"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { NaturePlan } from "../../types";
import { createSeededRandom, hashString } from "../../world/random";

export function FireflySwarm({ plan }: { plan: NaturePlan }) {
    const points = useRef<THREE.Points>(null);
    const positions = useMemo(() => {
        const random = createSeededRandom(hashString(plan.id));
        const data = new Float32Array(20 * 3);

        for (let index = 0; index < 20; index += 1) {
            data[index * 3] = plan.position[0] + (random() - 0.5) * 4;
            data[index * 3 + 1] = plan.position[1] + 0.8 + random() * 2.5;
            data[index * 3 + 2] = plan.position[2] + (random() - 0.5) * 4;
        }

        return data;
    }, [plan]);

    useFrame(({ clock }) => {
        if (points.current) {
            points.current.position.y = Math.sin(clock.elapsedTime * 0.4) * 0.2;
        }
    });

    return (
        <points ref={points}>
            <bufferGeometry>
                <bufferAttribute args={[positions, 3]} attach="attributes-position" />
            </bufferGeometry>
            <pointsMaterial
                blending={THREE.AdditiveBlending}
                color="#FFC857"
                depthWrite={false}
                opacity={0.86}
                size={0.09}
                transparent
            />
        </points>
    );
}
