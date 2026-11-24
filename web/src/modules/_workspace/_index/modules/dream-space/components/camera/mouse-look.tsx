"use client";

import { useEffect } from "react";

import { useThree } from "@react-three/fiber";

import { playerRuntime } from "../../world/player-runtime";

const MAX_PITCH = Math.PI / 2 - 0.12;

export function requestPointerLock(): void {
    if (typeof document === "undefined") {
        return;
    }

    void document.querySelector<HTMLCanvasElement>("[data-dream-canvas] canvas")?.requestPointerLock?.();
}

export function MouseLook() {
    const gl = useThree((state) => state.gl);

    useEffect(() => {
        const canvas = gl.domElement;
        let dragging = false;
        let lastX = 0;
        let lastY = 0;

        const onPointerDown = (event: PointerEvent) => {
            if (event.button !== 0) {
                return;
            }

            dragging = true;
            lastX = event.clientX;
            lastY = event.clientY;
            canvas.setPointerCapture(event.pointerId);
        };

        const onPointerMove = (event: PointerEvent) => {
            if (!dragging && document.pointerLockElement !== canvas) {
                return;
            }

            const movementX = document.pointerLockElement === canvas ? event.movementX : event.clientX - lastX;
            const movementY = document.pointerLockElement === canvas ? event.movementY : event.clientY - lastY;
            lastX = event.clientX;
            lastY = event.clientY;
            playerRuntime.yaw -= movementX * 0.0028;
            playerRuntime.pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, playerRuntime.pitch - movementY * 0.0024));
        };

        const onPointerUp = (event: PointerEvent) => {
            dragging = false;

            if (canvas.hasPointerCapture(event.pointerId)) {
                canvas.releasePointerCapture(event.pointerId);
            }
        };

        const onPointerLockChange = () => {
            playerRuntime.input.pointerLocked = document.pointerLockElement === canvas;
        };

        canvas.addEventListener("pointerdown", onPointerDown);
        canvas.addEventListener("pointermove", onPointerMove);
        canvas.addEventListener("pointerup", onPointerUp);
        canvas.addEventListener("pointercancel", onPointerUp);
        document.addEventListener("pointerlockchange", onPointerLockChange);

        return () => {
            canvas.removeEventListener("pointerdown", onPointerDown);
            canvas.removeEventListener("pointermove", onPointerMove);
            canvas.removeEventListener("pointerup", onPointerUp);
            canvas.removeEventListener("pointercancel", onPointerUp);
            document.removeEventListener("pointerlockchange", onPointerLockChange);
        };
    }, [gl]);

    return null;
}
