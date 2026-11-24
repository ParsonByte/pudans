"use client";

import type { RegionTheme } from "../../types";

export function SkyColorGradient({ density = 0.0125, theme }: { density?: number; theme: RegionTheme }) {
    return (
        <>
            <fogExp2 attach="fog" args={[theme.fog, density]} />
            <color attach="background" args={[theme.sky.top]} />
        </>
    );
}
