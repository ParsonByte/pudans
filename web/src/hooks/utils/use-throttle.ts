"use client";

import { useEffect, useRef, useState } from "react";

export function useThrottle<T>(value: T, interval = 300) {
    const [throttledValue, setThrottledValue] = useState(value);
    const lastRunRef = useRef(0);

    useEffect(() => {
        const elapsed = Date.now() - lastRunRef.current;
        const delay = elapsed >= interval ? 0 : interval - elapsed;

        const timer = setTimeout(() => {
            lastRunRef.current = Date.now();
            setThrottledValue(value);
        }, delay);

        return () => clearTimeout(timer);
    }, [value, interval]);

    return throttledValue;
}
