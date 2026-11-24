"use client";

import { type ReactNode, useEffect, useMemo, useState } from "react";

import { useSearchParams } from "next/navigation";

import { useConsoleSession } from "@/modules/_workspace/_console/composables/_session";

import { SESSION_STORAGE_KEY } from "../../constants";
import { initializeDreamWorld } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import { createSessionId } from "../../world/seed";
import { WorldLoader } from "./world-loader";

function readOrCreateSessionId(providedSessionId: string | null): string {
    if (providedSessionId) {
        window.sessionStorage.setItem(SESSION_STORAGE_KEY, providedSessionId);
        return providedSessionId;
    }

    const stored = window.sessionStorage.getItem(SESSION_STORAGE_KEY);

    if (stored) {
        return stored;
    }

    const created = createSessionId();
    window.sessionStorage.setItem(SESSION_STORAGE_KEY, created);

    return created;
}

export function WorldSessionProvider({ children }: { children: ReactNode }) {
    const searchParams = useSearchParams();
    const providedSessionId = searchParams.get("session");
    const sessionQuery = useConsoleSession();
    const phase = useDreamStore((state) => state.phase);
    const [sessionId, setSessionId] = useState<string | null>(null);
    const user = useMemo(
        () => ({
            displayName: sessionQuery.data?.displayName || sessionQuery.data?.username || "梦境旅人",
            userId: sessionQuery.data?.username || sessionQuery.data?.displayName || "dreamer",
        }),
        [sessionQuery.data?.displayName, sessionQuery.data?.username],
    );

    useEffect(() => {
        const timer = window.setTimeout(() => setSessionId(readOrCreateSessionId(providedSessionId)), 0);

        return () => window.clearTimeout(timer);
    }, [providedSessionId]);

    useEffect(() => {
        if (!sessionId || sessionQuery.isPending) {
            return;
        }

        initializeDreamWorld(user.userId, sessionId);
    }, [sessionId, sessionQuery.isPending, user.userId]);

    if (phase === "loading" || !sessionId) {
        return <WorldLoader label="正在根据你的会话折叠天地" />;
    }

    return (
        <div className="contents" data-dream-user={user.displayName}>
            {children}
        </div>
    );
}

export function useDreamWorldUser(): { displayName: string; userId: string } {
    const sessionQuery = useConsoleSession();

    return {
        displayName: sessionQuery.data?.displayName || sessionQuery.data?.username || "梦境旅人",
        userId: sessionQuery.data?.username || sessionQuery.data?.displayName || "dreamer",
    };
}
