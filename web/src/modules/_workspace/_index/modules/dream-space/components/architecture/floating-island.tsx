"use client";

import { useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import type { StructurePlan } from "../../types";

export function FloatingIsland({ plan }: { plan: StructurePlan }) {
    const group = useRef<THREE.Group>(null);

    useFrame(({ clock }) => {
        if (group.current) {
            group.current.position.y =
                plan.position[1] + 7 + Math.sin(clock.elapsedTime * 0.3 + plan.position[0]) * 0.5;
        }
    });

    return (
        <group position={[plan.position[0], plan.position[1] + 7, plan.position[2]]} ref={group} scale={plan.scale}>
            <mesh>
                <dodecahedronGeometry args={[3.2, 0]} />
                <meshStandardMaterial color="#11162d" metalness={0.18} roughness={0.8} />
            </mesh>
            <mesh position={[0, 2.1, 0]}>
                <sphereGeometry args={[1.15, 16, 12]} />
                <meshStandardMaterial
                    color={plan.accent}
                    emissive={plan.accent}
                    emissiveIntensity={1.2}
                    toneMapped={false}
                />
            </mesh>
            <mesh rotation={[Math.PI / 2.2, 0, 0.2]}>
                <ringGeometry args={[4.8, 5.03, 72]} />
                <meshBasicMaterial blending={THREE.AdditiveBlending} color={plan.accent} transparent opacity={0.3} />
            </mesh>
        </group>
    );
}
