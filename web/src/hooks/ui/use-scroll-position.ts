"use client";

import { useMemo, useSyncExternalStore } from "react";

function subscribe(onChange: () => void) {
    window.addEventListener("scroll", onChange, { passive: true });
    return () => window.removeEventListener("scroll", onChange);
}

function getSnapshot() {
    return `${window.scrollX},${window.scrollY}`;
}

function getServerSnapshot() {
    return "0,0";
}

export function useScrollPosition() {
    const snapshot = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

    return useMemo(() => {
        const [x, y] = snapshot.split(",").map(Number);
        return { x, y };
    }, [snapshot]);
}
