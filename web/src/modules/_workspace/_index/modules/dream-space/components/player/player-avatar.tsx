"use client";

import { useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import { useDreamStore } from "../../stores/use-dream-store";
import { playerRuntime } from "../../world/player-runtime";

export function PlayerAvatar() {
    const group = useRef<THREE.Group>(null);
    const cloak = useRef<THREE.Mesh>(null);
    const cameraMode = useDreamStore((state) => state.cameraMode);

    useFrame(({ clock }) => {
        if (!group.current) {
            return;
        }

        group.current.position.copy(playerRuntime.position);
        group.current.position.y -= 1.36;
        group.current.rotation.y = playerRuntime.yaw;

        if (cloak.current) {
            cloak.current.rotation.z = Math.sin(clock.elapsedTime * 1.4) * (playerRuntime.speed > 0.2 ? 0.06 : 0.015);
        }
    });

    return (
        <group ref={group} visible={cameraMode === "third"}>
            <mesh position={[0, 0.94, 0.02]}>
                <capsuleGeometry args={[0.28, 0.62, 6, 12]} />
                <meshStandardMaterial
                    color="#182146"
                    emissive="#6D5DFC"
                    emissiveIntensity={0.24}
                    metalness={0.52}
                    roughness={0.34}
                />
            </mesh>
            <mesh position={[0, 1.55, 0]}>
                <sphereGeometry args={[0.22, 18, 12]} />
                <meshStandardMaterial color="#DDD8FF" emissive="#9C8CFF" emissiveIntensity={0.24} roughness={0.4} />
            </mesh>
            <mesh position={[0, 1.12, -0.24]} ref={cloak}>
                <coneGeometry args={[0.5, 1.25, 5, 1, true]} />
                <meshStandardMaterial
                    color="#0F1636"
                    emissive="#39D0FF"
                    emissiveIntensity={0.08}
                    metalness={0.32}
                    roughness={0.48}
                    side={THREE.DoubleSide}
                />
            </mesh>
            <mesh position={[0, 1.88, 0]} rotation={[Math.PI / 2, 0, 0]}>
                <ringGeometry args={[0.33, 0.36, 32]} />
                <meshBasicMaterial blending={THREE.AdditiveBlending} color="#56D9FF" transparent opacity={0.38} />
            </mesh>
        </group>
    );
}
