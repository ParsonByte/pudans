"use client";

import { useRef } from "react";

import styles from "../../styles/dream-space.module.css";
import { playerRuntime } from "../../world/player-runtime";

const MAX_OFFSET = 30;

export function VirtualJoystick() {
    const knob = useRef<HTMLDivElement>(null);
    const activePointer = useRef<number | null>(null);
    const origin = useRef({ x: 0, y: 0 });

    const update = (clientX: number, clientY: number) => {
        const dx = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, clientX - origin.current.x));
        const dy = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, clientY - origin.current.y));
        const magnitude = Math.hypot(dx, dy);
        const normalized = magnitude > MAX_OFFSET ? MAX_OFFSET / magnitude : 1;

        playerRuntime.input.joystickX = (dx * normalized) / MAX_OFFSET;
        playerRuntime.input.joystickY = (dy * normalized) / MAX_OFFSET;

        if (knob.current) {
            knob.current.style.transform = `translate(${dx * normalized}px, ${dy * normalized}px)`;
        }
    };

    const release = () => {
        activePointer.current = null;
        playerRuntime.input.joystickX = 0;
        playerRuntime.input.joystickY = 0;

        if (knob.current) {
            knob.current.style.transform = "translate(0, 0)";
        }
    };

    return (
        <div
            aria-label="移动摇杆"
            className={styles.joystick}
            onPointerCancel={release}
            onPointerDown={(event) => {
                activePointer.current = event.pointerId;
                origin.current = { x: event.clientX, y: event.clientY };
                event.currentTarget.setPointerCapture(event.pointerId);
                update(event.clientX, event.clientY);
            }}
            onPointerMove={(event) => {
                if (activePointer.current === event.pointerId) {
                    update(event.clientX, event.clientY);
                }
            }}
            onPointerUp={release}
            role="presentation"
        >
            <div className={styles.joystickKnob} ref={knob} />
        </div>
    );
}
