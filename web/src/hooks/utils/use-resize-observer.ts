"use client";

import { useEffect, useRef, useState } from "react";

export function useResizeObserver<T extends Element>() {
    const ref = useRef<T>(null);
    const [size, setSize] = useState({ width: 0, height: 0 });

    useEffect(() => {
        const element = ref.current;
        if (!element) {
            return;
        }

        const observer = new ResizeObserver((entries) => {
            const entry = entries[0];
            if (!entry) {
                return;
            }

            const { width, height } = entry.contentRect;
            setSize((previous) =>
                previous.width === width && previous.height === height ? previous : { width, height },
            );
        });

        observer.observe(element);
        return () => observer.disconnect();
    }, []);

    return { ref, width: size.width, height: size.height };
}
