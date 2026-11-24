"use client";

import { useEffect } from "react";

import { useDreamStore } from "../../stores/use-dream-store";

export function HighContrastMode() {
    const highContrast = useDreamStore((state) => state.highContrast);

    useEffect(() => {
        document.documentElement.classList.toggle("dream-high-contrast", highContrast);

        return () => document.documentElement.classList.remove("dream-high-contrast");
    }, [highContrast]);

    return null;
}
