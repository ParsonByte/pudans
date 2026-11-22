import { createStore } from "../create-store";

export type SetupState = {
    completed: boolean;
};

export const setupStore = createStore<SetupState>({
    completed: false,
});

export function markSetupCompleted(): void {
    setupStore.setState((state) => ({ ...state, completed: true }));
}
