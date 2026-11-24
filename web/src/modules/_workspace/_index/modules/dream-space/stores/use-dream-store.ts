"use client";

import { useSyncExternalStore } from "react";

import { dreamStore } from "./dream-store";

export function useDreamStore<T>(selector: (state: ReturnType<typeof dreamStore.getState>) => T): T {
    return useSyncExternalStore(
        dreamStore.subscribe,
        () => selector(dreamStore.getState()),
        () => selector(dreamStore.getState()),
    );
}
