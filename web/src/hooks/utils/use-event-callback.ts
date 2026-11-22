"use client";

import { useCallback, useEffect, useRef } from "react";

export function useEventCallback<Args extends unknown[], Return>(callback: (...args: Args) => Return) {
    const callbackRef = useRef(callback);

    useEffect(() => {
        callbackRef.current = callback;
    }, [callback]);

    return useCallback((...args: Args) => callbackRef.current(...args), []);
}
