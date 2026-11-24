"use client";

import { useMemo } from "react";

import * as THREE from "three";

import type { RegionDefinition } from "../../types";
import { createTerrainSurface } from "../../world/terrain";
import { GroundMaterial } from "./ground-material";

export function GroundMesh({ region }: { region: RegionDefinition }) {
    const surface = useMemo(() => createTerrainSurface(region), [region]);
    const geometry = useMemo(() => {
        const nextGeometry = new THREE.BufferGeometry();
        nextGeometry.setAttribute("position", new THREE.BufferAttribute(surface.positions, 3));
        nextGeometry.setAttribute("color", new THREE.BufferAttribute(surface.colors, 3));
        nextGeometry.setIndex(new THREE.BufferAttribute(surface.indices, 1));
        nextGeometry.computeVertexNormals();

        return nextGeometry;
    }, [surface]);

    return (
        <mesh geometry={geometry} receiveShadow>
            <GroundMaterial theme={region.theme} />
        </mesh>
    );
}
