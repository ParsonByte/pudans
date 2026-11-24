"use client";

import { useRef } from "react";

import { Html } from "@react-three/drei";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import styles from "../../styles/dream-space.module.css";
import type { RegionDefinition } from "../../types";

export function NPCWanderer({ region }: { region: RegionDefinition }) {
    const group = useRef<THREE.Group>(null);

    useFrame(({ clock }) => {
        if (!group.current) {
            return;
        }

        const radius = 6.8;
        const angle = clock.elapsedTime * 0.07 + region.terrain.seed * 0.0001;
        group.current.position.set(
            region.center[0] + Math.cos(angle) * radius,
            0.5,
            region.center[2] + Math.sin(angle) * radius,
        );
        group.current.rotation.y = -angle;
    });

    return (
        <group ref={group}>
            <mesh position={[0, 0.4, 0]}>
                <capsuleGeometry args={[0.16, 0.46, 4, 8]} />
                <meshStandardMaterial color="#20284a" emissive={region.theme.accent} emissiveIntensity={0.22} />
            </mesh>
            <mesh position={[0, 0.98, 0]}>
                <sphereGeometry args={[0.17, 12, 8]} />
                <meshStandardMaterial color="#DDD8FF" emissive={region.theme.accent} emissiveIntensity={0.2} />
            </mesh>
            <Html center className={styles.npcBubble} distanceFactor={8} position={[0, 1.55, 0]}>
                回声行者
            </Html>
        </group>
    );
}
