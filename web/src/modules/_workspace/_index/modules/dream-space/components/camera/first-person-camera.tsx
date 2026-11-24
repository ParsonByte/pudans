"use client";

import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

import { cameraRuntime } from "../../world/camera-runtime";
import { playerRuntime } from "../../world/player-runtime";

const forward = new THREE.Vector3();

export function FirstPersonCamera() {
    useFrame(() => {
        if (playerRuntime.cameraMode !== "first") {
            return;
        }

        const headX = playerRuntime.position.x;
        const headY = playerRuntime.position.y + 0.52;
        const headZ = playerRuntime.position.z;
        const cosPitch = Math.cos(playerRuntime.pitch);

        forward.set(
            -Math.sin(playerRuntime.yaw) * cosPitch,
            Math.sin(playerRuntime.pitch),
            -Math.cos(playerRuntime.yaw) * cosPitch,
        );
        cameraRuntime.desiredPosition.set(headX, headY, headZ);
        cameraRuntime.lookTarget.set(headX + forward.x * 12, headY + forward.y * 12, headZ + forward.z * 12);
        cameraRuntime.collisionDistance = 0;
    });

    return null;
}
