"use client";

import { useEffect, useRef } from "react";

import { useFrame } from "@react-three/fiber";
import { type RapierRigidBody, RigidBody, useRapier } from "@react-three/rapier";

import { setDreamPlayerPose } from "../../stores/dream-store";
import { dreamStore } from "../../stores/dream-store";
import type { RegionDefinition } from "../../types";
import { activateInteractionTarget } from "../../world/interactions";
import { clearPlayerRuntimeInput, playerRuntime, resetPlayerRuntime } from "../../world/player-runtime";
import { PlayerCollider } from "./player-collider";

function isEditableTarget(target: EventTarget | null): boolean {
    return (
        target instanceof HTMLElement &&
        (target.tagName === "INPUT" ||
            target.tagName === "TEXTAREA" ||
            target.tagName === "SELECT" ||
            target.isContentEditable)
    );
}

export function PlayerMovement({ region }: { region: RegionDefinition }) {
    const body = useRef<RapierRigidBody>(null);
    const { world } = useRapier();
    const controller = useRef<ReturnType<typeof world.createCharacterController> | null>(null);
    const velocityY = useRef(0);

    useEffect(() => {
        const characterController = world.createCharacterController(0.02);
        characterController.enableAutostep(0.42, 0.2, true);
        characterController.enableSnapToGround(0.4);
        characterController.setMaxSlopeClimbAngle((50 * Math.PI) / 180);
        characterController.setMinSlopeSlideAngle((32 * Math.PI) / 180);
        controller.current = characterController;

        return () => {
            controller.current = null;
            world.removeCharacterController(characterController);
        };
    }, [world]);

    useEffect(() => {
        resetPlayerRuntime(region.spawn);
    }, [region.id, region.spawn]);

    useEffect(() => {
        const setKey = (event: KeyboardEvent, pressed: boolean) => {
            if (isEditableTarget(event.target)) {
                return;
            }

            if (event.code === "KeyW" || event.code === "ArrowUp") {
                playerRuntime.input.forward = pressed;
            }
            if (event.code === "KeyS" || event.code === "ArrowDown") {
                playerRuntime.input.backward = pressed;
            }
            if (event.code === "KeyA" || event.code === "ArrowLeft") {
                playerRuntime.input.left = pressed;
            }
            if (event.code === "KeyD" || event.code === "ArrowRight") {
                playerRuntime.input.right = pressed;
            }
            if (event.code === "ShiftLeft" || event.code === "ShiftRight") {
                playerRuntime.input.run = pressed;
            }
            if (pressed && event.code === "KeyE") {
                playerRuntime.interactRequested = true;
            }
        };
        const onKeyDown = (event: KeyboardEvent) => setKey(event, true);
        const onKeyUp = (event: KeyboardEvent) => setKey(event, false);
        const onBlur = () => {
            clearPlayerRuntimeInput();
        };

        window.addEventListener("keydown", onKeyDown);
        window.addEventListener("keyup", onKeyUp);
        window.addEventListener("blur", onBlur);

        return () => {
            window.removeEventListener("keydown", onKeyDown);
            window.removeEventListener("keyup", onKeyUp);
            window.removeEventListener("blur", onBlur);
        };
    }, []);

    useFrame((_, rawDelta) => {
        const characterController = controller.current;

        if (!body.current || !characterController) {
            return;
        }

        const state = dreamStore.getState();
        const collider = body.current.collider(0);

        if (!collider) {
            return;
        }

        playerRuntime.colliderHandle = collider.handle;

        const delta = Math.min(rawDelta, 0.05);
        const input = playerRuntime.input;
        const forwardAmount = (input.forward ? 1 : 0) - (input.backward ? 1 : 0) - input.joystickY;
        const strafeAmount = (input.right ? 1 : 0) - (input.left ? 1 : 0) + input.joystickX;
        const magnitude = Math.min(1, Math.hypot(forwardAmount, strafeAmount));
        const canMove = state.phase === "ready" && !state.paused;
        const running = input.run && playerRuntime.stamina > 0.08;
        const speed = running ? 6.4 : 3.5;
        const correctedForward = forwardAmount * (magnitude > 1 ? 1 / magnitude : 1);
        const correctedStrafe = strafeAmount * (magnitude > 1 ? 1 / magnitude : 1);
        const forwardX = -Math.sin(playerRuntime.yaw);
        const forwardZ = -Math.cos(playerRuntime.yaw);
        const rightX = Math.cos(playerRuntime.yaw);
        const rightZ = -Math.sin(playerRuntime.yaw);
        const desiredX = canMove ? (forwardX * correctedForward + rightX * correctedStrafe) * speed * delta : 0;
        const desiredZ = canMove ? (forwardZ * correctedForward + rightZ * correctedStrafe) * speed * delta : 0;

        velocityY.current = Math.max(-16, velocityY.current - 24 * delta);
        characterController.computeColliderMovement(collider, {
            x: desiredX,
            y: velocityY.current * delta,
            z: desiredZ,
        });

        const movement = characterController.computedMovement();
        const current = body.current.translation();
        const next = {
            x: current.x + movement.x,
            y: current.y + movement.y,
            z: current.z + movement.z,
        };

        body.current.setNextKinematicTranslation(next);
        playerRuntime.position.set(next.x, next.y, next.z);
        playerRuntime.speed = Math.hypot(desiredX, desiredZ) / Math.max(delta, 0.001);
        playerRuntime.grounded = characterController.computedGrounded();

        if (playerRuntime.grounded && velocityY.current < 0) {
            velocityY.current = 0;
        }

        if (playerRuntime.interactRequested) {
            playerRuntime.interactRequested = false;
            activateInteractionTarget(dreamStore.getState().interactionTarget);
        }

        const now = performance.now();

        if (now - playerRuntime.lastPublishedAt > 120) {
            playerRuntime.lastPublishedAt = now;
            setDreamPlayerPose({
                grounded: playerRuntime.grounded,
                pitch: playerRuntime.pitch,
                position: [next.x, next.y, next.z],
                stamina: playerRuntime.stamina,
                yaw: playerRuntime.yaw,
            });
        }
    });

    return (
        <RigidBody
            canSleep={false}
            colliders={false}
            enabledRotations={[false, false, false]}
            key={`${region.id}-${region.spawn.join("-")}`}
            position={region.spawn}
            ref={body}
            type="kinematicPosition"
        >
            <PlayerCollider />
        </RigidBody>
    );
}
