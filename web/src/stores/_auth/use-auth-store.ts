"use client";

import { useStore } from "../use-store";
import { type AuthState, authStore } from "./store";

export function useAuthStore(): AuthState {
    return useStore(authStore);
}
