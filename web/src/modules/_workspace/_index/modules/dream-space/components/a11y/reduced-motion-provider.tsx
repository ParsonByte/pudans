"use client";

import { type ReactNode, useEffect } from "react";

import { setDreamReducedMotion } from "../../stores/dream-store";

export function ReducedMotionProvider({ children }: { children: ReactNode }) {
    useEffect(() => {
        const media = window.matchMedia("(prefers-reduced-motion: reduce)");
        const update = () => setDreamReducedMotion(media.matches);
        update();
        media.addEventListener("change", update);

        return () => media.removeEventListener("change", update);
    }, []);

    return <>{children}</>;
}
