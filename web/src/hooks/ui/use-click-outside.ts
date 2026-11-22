"use client";

import { useRef } from "react";

import { useEventListener } from "@/hooks/utils/use-event-listener";

export function useClickOutside<T extends HTMLElement>(onOutside: (event: PointerEvent) => void, enabled = true) {
    const ref = useRef<T>(null);

    useEventListener(
        "pointerdown",
        (event) => {
            const element = ref.current;
            if (element && event.target instanceof Node && !element.contains(event.target)) {
                onOutside(event);
            }
        },
        enabled && typeof document !== "undefined" ? document : null,
    );

    return ref;
}
