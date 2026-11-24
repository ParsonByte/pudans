"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { RegionDefinition } from "../../types";
import { createSeededRandom } from "../../world/random";

export function DreamFogParticles({ reducedMotion, region }: { reducedMotion: boolean; region: RegionDefinition }) {
    const points = useRef<THREE.Points>(null);
    const positions = useMemo(() => {
        const random = createSeededRandom(region.terrain.seed ^ 0xf060);
        const data = new Float32Array(110 * 3);

        for (let index = 0; index < 110; index += 1) {
            data[index * 3] = (random() - 0.5) * 44;
            data[index * 3 + 1] = 0.2 + random() * 2.2;
            data[index * 3 + 2] = (random() - 0.5) * 44;
        }

        return data;
    }, [region]);

    useFrame(({ clock }) => {
        if (points.current && !reducedMotion) {
            points.current.rotation.y = clock.elapsedTime * 0.012;
        }
    });

    return (
        <points position={region.center} ref={points}>
            <bufferGeometry>
                <bufferAttribute args={[positions, 3]} attach="attributes-position" />
            </bufferGeometry>
            <pointsMaterial
                blending={THREE.AdditiveBlending}
                color={region.theme.secondary}
                depthWrite={false}
                opacity={0.14}
                size={0.32}
                transparent
            />
        </points>
    );
}
