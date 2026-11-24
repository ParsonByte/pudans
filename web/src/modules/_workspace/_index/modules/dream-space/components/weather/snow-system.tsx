"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { RegionDefinition } from "../../types";
import { createSeededRandom } from "../../world/random";

export function SnowSystem({ region, reducedMotion }: { region: RegionDefinition; reducedMotion: boolean }) {
    const points = useRef<THREE.Points>(null);
    const data = useMemo(() => {
        const random = createSeededRandom(region.terrain.seed ^ 0x5a0a);
        const count = 260;
        const positions = new Float32Array(count * 3);

        for (let index = 0; index < count; index += 1) {
            positions[index * 3] = (random() - 0.5) * 48;
            positions[index * 3 + 1] = 1 + random() * 22;
            positions[index * 3 + 2] = (random() - 0.5) * 48;
        }

        return positions;
    }, [region]);

    useFrame(({ clock }) => {
        if (!points.current || reducedMotion) {
            return;
        }

        const positions = points.current.geometry.getAttribute("position") as THREE.BufferAttribute;
        const array = positions.array as Float32Array;

        for (let index = 0; index < array.length; index += 3) {
            array[index + 1] -= 0.018 + (index % 5) * 0.003;
            array[index] += Math.sin(clock.elapsedTime * 0.4 + index) * 0.003;

            if (array[index + 1] < 0) {
                array[index + 1] = 22;
            }
        }

        positions.needsUpdate = true;
    });

    return (
        <points frustumCulled={false} position={region.center} ref={points}>
            <bufferGeometry>
                <bufferAttribute args={[data, 3]} attach="attributes-position" />
            </bufferGeometry>
            <pointsMaterial
                blending={THREE.AdditiveBlending}
                color={region.theme.star}
                depthWrite={false}
                opacity={0.46}
                size={0.11}
                transparent
            />
        </points>
    );
}
