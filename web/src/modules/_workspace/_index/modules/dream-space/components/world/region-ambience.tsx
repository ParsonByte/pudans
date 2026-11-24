"use client";

import type { RegionDefinition } from "../../types";

export function RegionAmbience({ region }: { region: RegionDefinition }) {
    return (
        <>
            <ambientLight color={region.theme.ambient} intensity={0.52} />
            <hemisphereLight color={region.theme.sky.horizon} groundColor={region.theme.groundEdge} intensity={1.1} />
            <directionalLight
                color={region.theme.star}
                intensity={1.15}
                position={[region.center[0] + 28, 42, region.center[2] - 24]}
                shadow-mapSize-height={1024}
                shadow-mapSize-width={1024}
            />
            <pointLight
                color={region.theme.accent}
                distance={42}
                intensity={2.8}
                position={[region.center[0], 12, region.center[2]]}
            />
        </>
    );
}
