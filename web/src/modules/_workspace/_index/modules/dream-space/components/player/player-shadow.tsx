"use client";

import { useRef } from "react";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import { playerRuntime } from "../../world/player-runtime";

export function PlayerShadow() {
    const mesh = useRef<THREE.Mesh>(null);

    useFrame(() => {
        if (mesh.current) {
            mesh.current.position.set(playerRuntime.position.x, 0.04, playerRuntime.position.z);
            mesh.current.scale.setScalar(0.82 + (playerRuntime.position.y - 1) * 0.08);
        }
    });

    return (
        <mesh ref={mesh} rotation={[-Math.PI / 2, 0, 0]}>
            <circleGeometry args={[0.5, 32]} />
            <meshBasicMaterial color="#000000" depthWrite={false} opacity={0.24} transparent />
        </mesh>
    );
}
