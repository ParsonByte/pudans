"use client";

import { useState } from "react";

export function usePrevious<T>(value: T) {
    const [state, setState] = useState<{ current: T; previous: T | undefined }>({
        current: value,
        previous: undefined,
    });

    if (!Object.is(state.current, value)) {
        setState({ current: value, previous: state.current });
    }

    return state.previous;
}
