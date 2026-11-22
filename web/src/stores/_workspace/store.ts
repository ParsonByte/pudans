import { createStore } from "../create-store";

export type WorkspaceState = {
    collapsed: boolean;
};

export const workspaceStore = createStore<WorkspaceState>({
    collapsed: false,
});

export function setWorkspaceCollapsed(collapsed: boolean): void {
    workspaceStore.setState((state) => ({ ...state, collapsed }));
}
