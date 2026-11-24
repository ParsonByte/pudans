"use client";

import { type ReactNode, createContext, useContext, useEffect, useMemo } from "react";

import type { WorldEvent, WorldEventListener } from "../../types";

type WorldEventBusValue = {
    emit: (event: WorldEvent) => void;
    subscribe: (listener: WorldEventListener) => () => void;
};

const WorldEventBusContext = createContext<WorldEventBusValue | null>(null);

export function WorldEventBus({ children }: { children: ReactNode }) {
    const value = useMemo<WorldEventBusValue>(() => {
        const listeners = new Set<WorldEventListener>();

        return {
            emit: (event) => listeners.forEach((listener) => listener(event)),
            subscribe: (listener) => {
                listeners.add(listener);
                return () => listeners.delete(listener);
            },
        };
    }, []);

    return <WorldEventBusContext.Provider value={value}>{children}</WorldEventBusContext.Provider>;
}

export function useWorldEventBus(): WorldEventBusValue {
    const value = useContext(WorldEventBusContext);

    if (!value) {
        throw new Error("WorldEventBus is missing");
    }

    return value;
}

export function WorldEventLogger() {
    const bus = useWorldEventBus();

    useEffect(
        () =>
            bus.subscribe((event) => {
                if (process.env.NODE_ENV === "development" && process.env.NEXT_PUBLIC_DREAM_DEBUG === "1") {
                    console.info("[DANS Dream]", event.type, event);
                }
            }),
        [bus],
    );

    return null;
}
