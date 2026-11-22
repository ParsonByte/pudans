"use client";

import { useSyncExternalStore } from "react";

import type { Store } from "./create-store";

export function useStore<T>(store: Store<T>): T {
    return useSyncExternalStore(store.subscribe, store.getState, store.getState);
}
