"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { RegionDefinition } from "../../types";
import { createSeededRandom } from "../../world/random";
import { skyRuntime } from "../../world/sky-runtime";

const VERTEX_SHADER = `
    attribute vec3 aColor;
    attribute float aPhase;
    attribute float aSize;
    uniform float uMotion;
    uniform float uTime;
    varying vec3 vColor;

    void main() {
        vec4 viewPosition = modelViewMatrix * vec4(position, 1.0);
        float pulse = 1.0 + sin(uTime * 0.62 + aPhase) * 0.22 * uMotion;
        gl_PointSize = aSize * pulse * (180.0 / max(1.0, -viewPosition.z));
        gl_Position = projectionMatrix * viewPosition;
        vColor = aColor;
    }
`;

const FRAGMENT_SHADER = `
    varying vec3 vColor;

    void main() {
        float distanceToCenter = length(gl_PointCoord - vec2(0.5));
        float alpha = 1.0 - smoothstep(0.05, 0.5, distanceToCenter);
        float core = 1.0 - smoothstep(0.0, 0.18, distanceToCenter);
        gl_FragColor = vec4(vColor + core * 0.18, alpha);
    }
`;

function createStars(region: RegionDefinition) {
    const random = createSeededRandom(region.terrain.seed ^ 0x57a25);
    const count = 1800;
    const positions = new Float32Array(count * 3);
    const colors = new Float32Array(count * 3);
    const sizes = new Float32Array(count);
    const phases = new Float32Array(count);
    const palette = [region.theme.star, region.theme.accent, region.theme.secondary].map(
        (color) => new THREE.Color(color),
    );

    for (let index = 0; index < count; index += 1) {
        const radius = 92 + random() * 92;
        const theta = random() * Math.PI * 2;
        const phi = Math.acos(0.18 + random() * 0.82);
        const x = Math.sin(phi) * Math.cos(theta) * radius;
        const y = Math.cos(phi) * radius + 5;
        const z = Math.sin(phi) * Math.sin(theta) * radius;
        const color = palette[Math.floor(random() * palette.length)];
        const brightness = 0.58 + random() * 0.42;

        positions[index * 3] = x;
        positions[index * 3 + 1] = y;
        positions[index * 3 + 2] = z;
        colors[index * 3] = color.r * brightness;
        colors[index * 3 + 1] = color.g * brightness;
        colors[index * 3 + 2] = color.b * brightness;
        sizes[index] = 0.55 + Math.pow(random(), 3) * 2.1;
        phases[index] = random() * Math.PI * 2;
    }

    return { colors, phases, positions, sizes };
}

export function StarField({ region, reducedMotion }: { region: RegionDefinition; reducedMotion: boolean }) {
    const material = useRef<THREE.ShaderMaterial>(null);
    const stars = useMemo(() => createStars(region), [region]);
    const uniforms = useMemo(
        () => ({
            uMotion: { value: reducedMotion ? 0 : 1 },
            uTime: { value: 0 },
        }),
        [reducedMotion],
    );

    useFrame(({ clock }) => {
        if (material.current) {
            material.current.uniforms.uTime.value = clock.elapsedTime;
            material.current.uniforms.uMotion.value = reducedMotion ? 0 : skyRuntime.twinkle;
        }
    });

    return (
        <points frustumCulled={false} position={region.center} renderOrder={-10}>
            <bufferGeometry>
                <bufferAttribute args={[stars.positions, 3]} attach="attributes-position" />
                <bufferAttribute args={[stars.colors, 3]} attach="attributes-aColor" />
                <bufferAttribute args={[stars.sizes, 1]} attach="attributes-aSize" />
                <bufferAttribute args={[stars.phases, 1]} attach="attributes-aPhase" />
            </bufferGeometry>
            <shaderMaterial
                blending={THREE.AdditiveBlending}
                depthWrite={false}
                fragmentShader={FRAGMENT_SHADER}
                ref={material}
                transparent
                uniforms={uniforms}
                vertexShader={VERTEX_SHADER}
            />
        </points>
    );
}
