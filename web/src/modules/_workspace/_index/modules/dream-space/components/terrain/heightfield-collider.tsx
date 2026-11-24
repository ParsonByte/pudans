"use client";

import { useMemo } from "react";

import { CuboidCollider, HeightfieldCollider as RapierHeightfield, RigidBody } from "@react-three/rapier";

import type { RegionDefinition } from "../../types";
import { createTerrainSurface } from "../../world/terrain";

export function HeightfieldCollider({ region }: { region: RegionDefinition }) {
    const surface = useMemo(() => createTerrainSurface(region), [region]);

    return (
        <RigidBody colliders={false} position={region.center} type="fixed">
            <RapierHeightfield
                args={[surface.segments, surface.segments, surface.heights, { x: surface.size, y: 1, z: surface.size }]}
            />
            <CuboidCollider args={[region.radius, 0.08, region.radius]} position={[0, -1.2, 0]} />
        </RigidBody>
    );
}
