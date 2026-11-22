"use client";

import { useStore } from "../use-store";
import { type SetupState, setupStore } from "./store";

export function useSetupStore(): SetupState {
    return useStore(setupStore);
}
