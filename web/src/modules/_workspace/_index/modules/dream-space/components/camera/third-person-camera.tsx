"use client";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import { cameraRuntime } from "../../world/camera-runtime";
import { playerRuntime } from "../../world/player-runtime";

const forward = new THREE.Vector3();

export function ThirdPersonCamera() {
    useFrame(() => {
        if (playerRuntime.cameraMode !== "third") {
            return;
        }

        const headX = playerRuntime.position.x;
        const headY = playerRuntime.position.y + 0.48;
        const headZ = playerRuntime.position.z;
        const cosPitch = Math.cos(playerRuntime.pitch);
        const distance = playerRuntime.cameraDistance;

        forward.set(
            -Math.sin(playerRuntime.yaw) * cosPitch,
            Math.sin(playerRuntime.pitch),
            -Math.cos(playerRuntime.yaw) * cosPitch,
        );
        cameraRuntime.desiredPosition.set(
            headX - forward.x * distance,
            headY - forward.y * distance + 0.6,
            headZ - forward.z * distance,
        );
        cameraRuntime.lookTarget.set(headX + forward.x * 7, headY + forward.y * 7, headZ + forward.z * 7);
        cameraRuntime.collisionDistance = distance;
    });

    return null;
}
