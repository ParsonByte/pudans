"use client";

import { useRef } from "react";

import { useFrame } from "@react-three/fiber";
import { useRapier } from "@react-three/rapier";
import * as THREE from "three";

import { cameraRuntime } from "../../world/camera-runtime";
import { playerRuntime } from "../../world/player-runtime";

const origin = new THREE.Vector3();
const direction = new THREE.Vector3();

export function CameraCollision() {
    const { rapier, world } = useRapier();
    const ray = useRef(new rapier.Ray({ x: 0, y: 0, z: 0 }, { x: 0, y: 0, z: 1 }));

    useFrame(() => {
        if (playerRuntime.cameraMode !== "third" || !playerRuntime.colliderHandle) {
            return;
        }

        origin.set(playerRuntime.position.x, playerRuntime.position.y + 0.52, playerRuntime.position.z);
        direction.copy(cameraRuntime.desiredPosition).sub(origin);
        const maxDistance = direction.length();

        if (maxDistance < 0.01) {
            return;
        }

        direction.normalize();
        ray.current.origin = origin;
        ray.current.dir = direction;
        const hit = world.castRay(
            ray.current,
            maxDistance,
            true,
            undefined,
            undefined,
            undefined,
            undefined,
            (collider) => collider.handle !== playerRuntime.colliderHandle,
        );

        if (hit && hit.timeOfImpact < maxDistance) {
            const safeDistance = Math.max(0.65, hit.timeOfImpact - 0.22);
            cameraRuntime.desiredPosition.copy(origin).addScaledVector(direction, safeDistance);
            cameraRuntime.collisionDistance = safeDistance;
        }
    });

    return null;
}
