"use client";

import * as THREE from "three";

import type { RegionDefinition } from "../../types";

export function PlayerSpawn({ region }: { region: RegionDefinition }) {
    return (
        <group position={[region.spawn[0], 0.04, region.spawn[2]]}>
            <mesh rotation={[-Math.PI / 2, 0, 0]}>
                <ringGeometry args={[1.2, 1.34, 64]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={region.theme.accent}
                    depthWrite={false}
                    opacity={0.18}
                    transparent
                />
            </mesh>
            <mesh position={[0, 0.8, 0]}>
                <cylinderGeometry args={[0.02, 0.02, 1.6, 4]} />
                <meshBasicMaterial color={region.theme.accent} transparent opacity={0.26} />
            </mesh>
        </group>
    );
}
