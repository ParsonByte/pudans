"use client";

import { useState } from "react";

import { useEventListener } from "@/hooks/utils/use-event-listener";

const LISTENER_OPTIONS: AddEventListenerOptions = { passive: true };

export function useMousePosition() {
    const [position, setPosition] = useState({ x: 0, y: 0 });

    useEventListener(
        "pointermove",
        (event) => {
            setPosition({ x: event.clientX, y: event.clientY });
        },
        undefined,
        LISTENER_OPTIONS,
    );

    return position;
}
