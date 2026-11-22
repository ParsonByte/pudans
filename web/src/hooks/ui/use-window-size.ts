"use client";

import { useMemo, useSyncExternalStore } from "react";

function subscribe(onChange: () => void) {
    window.addEventListener("resize", onChange, { passive: true });
    return () => window.removeEventListener("resize", onChange);
}

function getSnapshot() {
    return `${window.innerWidth},${window.innerHeight}`;
}

function getServerSnapshot() {
    return "0,0";
}

export function useWindowSize() {
    const snapshot = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

    return useMemo(() => {
        const [width, height] = snapshot.split(",").map(Number);
        return { width, height };
    }, [snapshot]);
}
