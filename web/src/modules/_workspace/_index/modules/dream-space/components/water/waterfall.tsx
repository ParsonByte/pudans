"use client";

import * as THREE from "three";

import type { WaterPlan } from "../../types";

export function Waterfall({ plan }: { plan: WaterPlan }) {
    return (
        <group position={plan.position} scale={plan.scale}>
            <mesh position={[0, 1.8, 0]}>
                <planeGeometry args={[1.4, 3.8]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={plan.accent}
                    depthWrite={false}
                    opacity={0.16}
                    side={THREE.DoubleSide}
                    transparent
                />
            </mesh>
            <mesh position={[0, 0.02, 0.7]} rotation={[-Math.PI / 2, 0, 0]}>
                <circleGeometry args={[1.2, 40]} />
                <meshBasicMaterial color={plan.accent} transparent opacity={0.16} />
            </mesh>
        </group>
    );
}
