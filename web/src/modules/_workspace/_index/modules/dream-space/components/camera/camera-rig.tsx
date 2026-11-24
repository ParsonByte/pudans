"use client";

import { useEffect, useRef } from "react";

import { useFrame, useThree } from "@react-three/fiber";
import * as THREE from "three";

import { useDreamStore } from "../../stores/use-dream-store";
import { cameraRuntime } from "../../world/camera-runtime";
import { playerRuntime } from "../../world/player-runtime";
import { CameraCollision } from "./camera-collision";
import { CameraFocusAnimator } from "./camera-focus-animator";
import { CameraShake } from "./camera-shake";
import { FirstPersonCamera } from "./first-person-camera";
import { ThirdPersonCamera } from "./third-person-camera";

const target = new THREE.Vector3();
const currentTarget = new THREE.Vector3();

export function CameraRig({ reducedMotion }: { reducedMotion: boolean }) {
    const camera = useThree((state) => state.camera);
    const cameraMode = useDreamStore((state) => state.cameraMode);
    const initialized = useRef(false);
    const cameraRef = useRef(camera);

    useEffect(() => {
        cameraRef.current = camera;
    }, [camera]);

    useFrame(({ clock }, delta) => {
        const activeCamera = cameraRef.current;
        playerRuntime.cameraMode = cameraMode;
        const blend = Math.min(1, delta * (cameraMode === "first" ? 18 : 8));

        if (!initialized.current) {
            activeCamera.position.copy(cameraRuntime.desiredPosition);
            currentTarget.copy(cameraRuntime.lookTarget);
            initialized.current = true;
        }

        activeCamera.position.lerp(cameraRuntime.desiredPosition, blend);
        target.copy(cameraRuntime.lookTarget);

        if (cameraRuntime.focusPoint) {
            target.lerp(cameraRuntime.focusPoint, 0.22);
        }

        currentTarget.lerp(target, Math.min(1, delta * 7));
        activeCamera.lookAt(currentTarget);

        if (!reducedMotion && cameraRuntime.shake > 0) {
            activeCamera.position.x += Math.sin(clock.elapsedTime * 52) * cameraRuntime.shake * 0.03;
            activeCamera.position.y += Math.cos(clock.elapsedTime * 43) * cameraRuntime.shake * 0.02;
        }
        playerRuntime.cameraDistance = cameraMode === "third" ? cameraRuntime.collisionDistance : 0;
    });

    return (
        <>
            <FirstPersonCamera />
            <ThirdPersonCamera />
            <CameraCollision />
            <CameraFocusAnimator />
            <CameraShake reducedMotion={reducedMotion} />
        </>
    );
}
