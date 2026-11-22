import type { SessionView } from "@pudans-dev/api-client";

import { createStore } from "../create-store";

export type AuthState = {
    session: SessionView | null;
    twoFactorUsername: string | null;
};

export const authStore = createStore<AuthState>({
    session: null,
    twoFactorUsername: null,
});

export function setAuthSession(session: SessionView): void {
    authStore.setState((state) => ({ ...state, session }));
}

export function startTwoFactorChallenge(username?: string): void {
    authStore.setState((state) => ({ ...state, twoFactorUsername: username ?? "" }));
}

export function cancelTwoFactorChallenge(): void {
    authStore.setState((state) => ({ ...state, twoFactorUsername: null }));
}
