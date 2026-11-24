import { WORLD_VERSION } from "../constants";
import { hashString } from "./random";

export type WorldSeed = {
    seed: number;
    shortCode: string;
    source: string;
    version: number;
};

export function createWorldSeed(userId: string, sessionId: string, version = WORLD_VERSION): WorldSeed {
    const source = `${userId.trim() || "anonymous"}:${sessionId.trim() || "session-0"}:${version}`;
    const seed = hashString(source);

    return {
        seed,
        shortCode: seed.toString(36).toUpperCase().padStart(7, "0").slice(-7),
        source,
        version,
    };
}

export function createSessionId(): string {
    if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
        return crypto.randomUUID();
    }

    return `dream-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}
