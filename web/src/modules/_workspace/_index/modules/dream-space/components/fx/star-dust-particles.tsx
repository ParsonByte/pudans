"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { RegionDefinition } from "../../types";
import { createSeededRandom } from "../../world/random";

export function StarDustParticles({
    count = 420,
    reducedMotion,
    region,
}: {
    count?: number;
    reducedMotion: boolean;
    region: RegionDefinition;
}) {
    const points = useRef<THREE.Points>(null);
    const positions = useMemo(() => {
        const random = createSeededRandom(region.terrain.seed ^ 0xd057);
        const data = new Float32Array(count * 3);

        for (let index = 0; index < count; index += 1) {
            data[index * 3] = (random() - 0.5) * 58;
            data[index * 3 + 1] = 1 + random() * 28;
            data[index * 3 + 2] = (random() - 0.5) * 58;
        }

        return data;
    }, [count, region]);

    useFrame(({ clock }) => {
        if (!points.current || reducedMotion) {
            return;
        }

        points.current.rotation.y = clock.elapsedTime * 0.008;
        points.current.position.y = Math.sin(clock.elapsedTime * 0.12) * 0.25;
    });

    return (
        <points position={region.center} ref={points} renderOrder={1}>
            <bufferGeometry>
                <bufferAttribute args={[positions, 3]} attach="attributes-position" />
            </bufferGeometry>
            <pointsMaterial
                blending={THREE.AdditiveBlending}
                color={region.theme.star}
                depthWrite={false}
                opacity={0.55}
                size={0.055}
                transparent
            />
        </points>
    );
}
