"use client";

import * as THREE from "three";

import type { Vec3 } from "../../types";

export function LightBeam({
    accent,
    from,
    intensity = 1,
    to,
}: {
    accent: string;
    from: Vec3;
    intensity?: number;
    to: Vec3;
}) {
    const start = new THREE.Vector3(...from);
    const end = new THREE.Vector3(...to);
    const midpoint = start.clone().add(end).multiplyScalar(0.5);
    const direction = end.clone().sub(start);
    const length = direction.length();
    const quaternion = new THREE.Quaternion().setFromUnitVectors(
        new THREE.Vector3(0, 1, 0),
        direction.clone().normalize(),
    );

    return (
        <group>
            <mesh position={midpoint} quaternion={quaternion}>
                <cylinderGeometry args={[0.009, 0.035, length, 6]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={accent}
                    depthWrite={false}
                    opacity={0.34 * intensity}
                    transparent
                />
            </mesh>
            <mesh position={[to[0], to[1] + 0.03, to[2]]} rotation={[-Math.PI / 2, 0, 0]}>
                <circleGeometry args={[0.34 + intensity * 0.18, 32]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={accent}
                    depthWrite={false}
                    opacity={0.14 * intensity}
                    transparent
                />
            </mesh>
        </group>
    );
}
