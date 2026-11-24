"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { RegionDefinition } from "../../types";
import { createSeededRandom } from "../../world/random";

export function RainSystem({ region, reducedMotion }: { region: RegionDefinition; reducedMotion: boolean }) {
    const points = useRef<THREE.Points>(null);
    const data = useMemo(() => {
        const random = createSeededRandom(region.terrain.seed ^ 0x7a1a);
        const count = 380;
        const positions = new Float32Array(count * 3);

        for (let index = 0; index < count; index += 1) {
            positions[index * 3] = (random() - 0.5) * 58;
            positions[index * 3 + 1] = 2 + random() * 24;
            positions[index * 3 + 2] = (random() - 0.5) * 58;
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
            array[index + 1] -= 0.13 + (index % 7) * 0.008;
            array[index] -= 0.012;

            if (array[index + 1] < 0) {
                array[index + 1] = 24 + (index % 9);
            }
        }

        positions.needsUpdate = true;
        points.current.rotation.y = clock.elapsedTime * 0.002;
    });

    return (
        <points frustumCulled={false} position={region.center} ref={points}>
            <bufferGeometry>
                <bufferAttribute args={[data, 3]} attach="attributes-position" />
            </bufferGeometry>
            <pointsMaterial
                blending={THREE.AdditiveBlending}
                color={region.theme.secondary}
                depthWrite={false}
                opacity={0.34}
                size={0.07}
                transparent
            />
        </points>
    );
}
