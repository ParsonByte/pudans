"use client";

import { useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { RegionDefinition } from "../../types";

export function MoonOrb({ region, reducedMotion }: { region: RegionDefinition; reducedMotion: boolean }) {
    const orb = useRef<THREE.Group>(null);

    useFrame(({ clock }) => {
        if (orb.current && !reducedMotion) {
            orb.current.position.y = region.center[1] + 52 + Math.sin(clock.elapsedTime * 0.08) * 0.8;
        }
    });

    return (
        <group position={[region.center[0] + 34, region.center[1] + 52, region.center[2] - 64]} ref={orb}>
            <mesh>
                <sphereGeometry args={[4.6, 36, 24]} />
                <meshBasicMaterial color={region.theme.star} toneMapped={false} />
            </mesh>
            <mesh scale={2.2}>
                <sphereGeometry args={[4.6, 24, 16]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={region.theme.accent}
                    depthWrite={false}
                    opacity={0.055}
                    transparent
                />
            </mesh>
            <mesh rotation={[Math.PI / 2.7, 0.32, 0]}>
                <ringGeometry args={[6.1, 6.7, 96]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={region.theme.secondary}
                    depthWrite={false}
                    opacity={0.18}
                    side={THREE.DoubleSide}
                    transparent
                />
            </mesh>
        </group>
    );
}
