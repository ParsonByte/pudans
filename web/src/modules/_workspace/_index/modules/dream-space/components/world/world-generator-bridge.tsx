"use client";

import type { ReactNode } from "react";
import { useEffect } from "react";

import { SESSION_STORAGE_KEY } from "../../constants";
import { initializeDreamWorld } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import { useDreamWorldUser } from "../core/world-session-provider";

export function WorldGeneratorBridge({ children }: { children: ReactNode }) {
    const world = useDreamStore((state) => state.world);
    const user = useDreamWorldUser();

    useEffect(() => {
        if (world || typeof window === "undefined") {
            return;
        }

        const sessionId = window.sessionStorage.getItem(SESSION_STORAGE_KEY);

        if (sessionId) {
            initializeDreamWorld(user.userId, sessionId);
        }
    }, [user.userId, world]);

    return <>{children}</>;
}
