type StoreListener = () => void;

export type Store<T> = {
    getState: () => T;
    setState: (updateState: (state: T) => T) => void;
    subscribe: (listener: StoreListener) => () => void;
};

export function createStore<T>(initialState: T): Store<T> {
    let state = initialState;
    const listeners = new Set<StoreListener>();

    return {
        getState: () => state,
        setState: (updateState) => {
            const nextState = updateState(state);

            if (Object.is(nextState, state)) {
                return;
            }

            state = nextState;
            listeners.forEach((listener) => listener());
        },
        subscribe: (listener) => {
            listeners.add(listener);

            return () => {
                listeners.delete(listener);
            };
        },
    };
}
