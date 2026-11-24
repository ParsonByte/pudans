"use client";

import * as THREE from "three";

import type { RegionDefinition, RegionTheme } from "../../types";

export function SunHalo({
    acceleration = 0,
    region,
    theme,
}: {
    acceleration?: number;
    region: RegionDefinition;
    theme: RegionTheme;
}) {
    return (
        <group position={[region.center[0] + 32, region.center[1] + 42, region.center[2] - 78]}>
            <mesh rotation={[0, 0, acceleration]}>
                <ringGeometry args={[7.5, 9.5, 96]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={theme.star}
                    depthWrite={false}
                    opacity={0.12}
                    side={THREE.DoubleSide}
                    transparent
                />
            </mesh>
            <mesh rotation={[0, 0, -acceleration * 0.6]} scale={1.45}>
                <ringGeometry args={[7.5, 8.2, 96]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={theme.secondary}
                    depthWrite={false}
                    opacity={0.09}
                    side={THREE.DoubleSide}
                    transparent
                />
            </mesh>
        </group>
    );
}
