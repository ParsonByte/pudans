"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { WaterPlan } from "../../types";

const VERTEX_SHADER = `
    uniform float uTime;
    varying vec2 vUv;

    void main() {
        vUv = uv;
        vec3 transformed = position;
        transformed.z += sin(position.x * 2.4 + uTime * 0.5) * 0.028;
        transformed.z += cos(position.y * 1.8 - uTime * 0.37) * 0.018;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(transformed, 1.0);
    }
`;

const FRAGMENT_SHADER = `
    uniform vec3 uColor;
    uniform float uTime;
    varying vec2 vUv;

    void main() {
        float wave = sin((vUv.x + vUv.y) * 20.0 + uTime * 0.8) * 0.5 + 0.5;
        float glow = smoothstep(0.7, 1.0, wave) * 0.12;
        gl_FragColor = vec4(uColor + glow, 0.3 + wave * 0.08);
    }
`;

export function DreamLake({ plan }: { plan: WaterPlan }) {
    const material = useRef<THREE.ShaderMaterial>(null);
    const uniforms = useMemo(
        () => ({
            uColor: { value: new THREE.Color(plan.accent).multiplyScalar(0.34) },
            uTime: { value: 0 },
        }),
        [plan.accent],
    );

    useFrame(({ clock }) => {
        if (material.current) {
            material.current.uniforms.uTime.value = clock.elapsedTime;
        }
    });

    return (
        <mesh position={[plan.position[0], 0.045, plan.position[2]]} rotation={[-Math.PI / 2, 0, 0]} scale={plan.scale}>
            <planeGeometry args={[1, 1, 40, 40]} />
            <shaderMaterial
                fragmentShader={FRAGMENT_SHADER}
                ref={material}
                transparent
                uniforms={uniforms}
                vertexShader={VERTEX_SHADER}
            />
        </mesh>
    );
}
