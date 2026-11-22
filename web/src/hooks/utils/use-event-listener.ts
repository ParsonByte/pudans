"use client";

import { useEffect, useRef } from "react";

type DomEventMap = WindowEventMap & DocumentEventMap;

export function useEventListener<K extends keyof DomEventMap>(
    type: K,
    listener: (event: DomEventMap[K]) => void,
    target?: EventTarget | null,
    options?: boolean | AddEventListenerOptions,
) {
    const listenerRef = useRef(listener);

    useEffect(() => {
        listenerRef.current = listener;
    }, [listener]);

    useEffect(() => {
        const eventTarget = target === undefined ? (typeof window === "undefined" ? null : window) : target;
        if (!eventTarget) {
            return;
        }

        const handler = (event: Event) => listenerRef.current(event as DomEventMap[K]);
        eventTarget.addEventListener(type, handler, options);
        return () => eventTarget.removeEventListener(type, handler, options);
    }, [type, target, options]);
}
