"use client";

import type { WaterPlan } from "../../types";

export function ReflectiveWater({ plan }: { plan: WaterPlan }) {
    return (
        <mesh position={[plan.position[0], 0.035, plan.position[2]]} rotation={[-Math.PI / 2, 0, 0]} scale={plan.scale}>
            <planeGeometry args={[1, 1, 24, 24]} />
            <meshPhysicalMaterial
                color={plan.accent}
                emissive={plan.accent}
                emissiveIntensity={0.12}
                metalness={0.84}
                opacity={0.22}
                roughness={0.12}
                toneMapped={false}
                transparent
            />
        </mesh>
    );
}
