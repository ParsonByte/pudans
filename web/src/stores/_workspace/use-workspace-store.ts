"use client";

import { useStore } from "../use-store";
import { type WorkspaceState, workspaceStore } from "./store";

export function useWorkspaceStore(): WorkspaceState {
    return useStore(workspaceStore);
}
