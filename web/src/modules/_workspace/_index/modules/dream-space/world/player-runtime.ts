import * as THREE from "three";

import type { CameraMode, Vec3 } from "../types";

export type PlayerRuntime = {
    cameraDistance: number;
    cameraMode: CameraMode;
    colliderHandle: number | null;
    grounded: boolean;
    input: {
        backward: boolean;
        forward: boolean;
        joystickX: number;
        joystickY: number;
        left: boolean;
        pointerLocked: boolean;
        right: boolean;
        run: boolean;
    };
    interactRequested: boolean;
    lastPublishedAt: number;
    pitch: number;
    position: THREE.Vector3;
    speed: number;
    stamina: number;
    velocity: THREE.Vector3;
    yaw: number;
};

export const playerRuntime: PlayerRuntime = {
    cameraDistance: 4,
    cameraMode: "third",
    colliderHandle: null,
    grounded: true,
    input: {
        backward: false,
        forward: false,
        joystickX: 0,
        joystickY: 0,
        left: false,
        pointerLocked: false,
        right: false,
        run: false,
    },
    interactRequested: false,
    lastPublishedAt: 0,
    pitch: -0.1,
    position: new THREE.Vector3(0, 1.1, 4),
    speed: 0,
    stamina: 1,
    velocity: new THREE.Vector3(),
    yaw: Math.PI,
};

export function resetPlayerRuntime(position: Vec3, yaw = Math.PI): void {
    playerRuntime.position.set(position[0], position[1], position[2]);
    playerRuntime.velocity.set(0, 0, 0);
    playerRuntime.yaw = yaw;
    playerRuntime.pitch = -0.1;
    playerRuntime.stamina = 1;
    playerRuntime.grounded = true;
    playerRuntime.speed = 0;
    playerRuntime.input.backward = false;
    playerRuntime.input.forward = false;
    playerRuntime.input.left = false;
    playerRuntime.input.right = false;
    playerRuntime.input.joystickX = 0;
    playerRuntime.input.joystickY = 0;
    playerRuntime.interactRequested = false;
}

export function clearPlayerRuntimeInput(): void {
    playerRuntime.input.backward = false;
    playerRuntime.input.forward = false;
    playerRuntime.input.left = false;
    playerRuntime.input.right = false;
    playerRuntime.input.joystickX = 0;
    playerRuntime.input.joystickY = 0;
    playerRuntime.input.run = false;
}
