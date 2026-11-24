"use client";

import { useMemo, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { RegionTheme } from "../../types";

const VERTEX_SHADER = `
    varying vec3 vWorldPosition;

    void main() {
        vec4 worldPosition = modelMatrix * vec4(position, 1.0);
        vWorldPosition = worldPosition.xyz;
        gl_Position = projectionMatrix * viewMatrix * worldPosition;
    }
`;

const FRAGMENT_SHADER = `
    uniform vec3 uTop;
    uniform vec3 uLower;
    uniform vec3 uHorizon;
    uniform float uTime;
    varying vec3 vWorldPosition;

    void main() {
        float height = smoothstep(-0.4, 1.0, normalize(vWorldPosition).y);
        float horizon = pow(1.0 - abs(normalize(vWorldPosition).y), 3.0);
        float drift = sin(vWorldPosition.x * 0.012 + uTime * 0.018) * cos(vWorldPosition.z * 0.009 - uTime * 0.012);
        vec3 color = mix(uHorizon, uLower, smoothstep(0.0, 0.42, height));
        color = mix(color, uTop, smoothstep(0.34, 1.0, height));
        color += uHorizon * horizon * 0.16;
        color += uLower * max(drift, 0.0) * 0.025;
        gl_FragColor = vec4(color, 1.0);
    }
`;

export function DreamSkyDome({ theme }: { theme: RegionTheme }) {
    const material = useRef<THREE.ShaderMaterial>(null);
    const uniforms = useMemo(
        () => ({
            uHorizon: { value: new THREE.Color(theme.sky.horizon) },
            uLower: { value: new THREE.Color(theme.sky.lower) },
            uTime: { value: 0 },
            uTop: { value: new THREE.Color(theme.sky.top) },
        }),
        [theme.sky.horizon, theme.sky.lower, theme.sky.top],
    );

    useFrame(({ clock }) => {
        if (material.current) {
            material.current.uniforms.uTime.value = clock.elapsedTime;
        }
    });

    return (
        <mesh frustumCulled={false} renderOrder={-20}>
            <sphereGeometry args={[220, 40, 24]} />
            <shaderMaterial
                depthWrite={false}
                fragmentShader={FRAGMENT_SHADER}
                ref={material}
                side={THREE.BackSide}
                uniforms={uniforms}
                vertexShader={VERTEX_SHADER}
            />
        </mesh>
    );
}
