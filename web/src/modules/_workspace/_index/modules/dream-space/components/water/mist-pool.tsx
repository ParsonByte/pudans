"use client";

import * as THREE from "three";

import type { WaterPlan } from "../../types";

export function MistPool({ plan }: { plan: WaterPlan }) {
    return (
        <group position={plan.position}>
            <mesh position={[0, 0.035, 0]} rotation={[-Math.PI / 2, 0, 0]} scale={plan.scale}>
                <circleGeometry args={[1, 48]} />
                <meshBasicMaterial color={plan.accent} transparent opacity={0.14} />
            </mesh>
            <mesh position={[0, 0.75, 0]} rotation={[-Math.PI / 2, 0, 0]}>
                <circleGeometry args={[3.2, 40]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={plan.accent}
                    depthWrite={false}
                    opacity={0.018}
                    transparent
                />
            </mesh>
        </group>
    );
}
