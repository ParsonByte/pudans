"use client";

import type { ReactNode } from "react";

import type { RegionDefinition } from "../../types";

export function TerrainGenerator({ children, region }: { children: ReactNode; region: RegionDefinition }) {
    return (
        <group name={`terrain-${region.id}`} position={region.center}>
            {children}
        </group>
    );
}
