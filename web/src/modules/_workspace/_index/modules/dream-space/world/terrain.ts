import * as THREE from "three";

import type { RegionDefinition } from "../types";
import { createSeededRandom } from "./random";

export type TerrainSurface = {
    colors: Float32Array;
    heights: number[];
    indices: Uint32Array;
    positions: Float32Array;
    segments: number;
    size: number;
};

function terrainHeightAtLocal(region: RegionDefinition, x: number, z: number): number {
    const distance = Math.hypot(x, z);
    const edge = THREE.MathUtils.clamp((distance - 7) / Math.max(1, region.radius - 7), 0, 1);
    const wave =
        Math.sin(x * region.terrain.frequency + region.terrain.seed * 0.001) *
            Math.cos(z * region.terrain.frequency * 1.21 - region.terrain.seed * 0.0007) +
        Math.sin((x + z) * region.terrain.frequency * 0.62) * 0.45;
    const detail = Math.sin(x * 0.42) * Math.cos(z * 0.37) * 0.08 * region.terrain.roughness;

    return (wave * region.terrain.amplitude + detail) * edge;
}

export function createTerrainSurface(region: RegionDefinition, size = 72, segments = 64): TerrainSurface {
    const positions = new Float32Array((segments + 1) * (segments + 1) * 3);
    const colors = new Float32Array((segments + 1) * (segments + 1) * 3);
    const heights: number[] = [];
    const indices: number[] = [];
    const random = createSeededRandom(region.terrain.seed);
    const base = new THREE.Color(region.theme.ground);
    const edge = new THREE.Color(region.theme.groundEdge);
    const accent = new THREE.Color(region.theme.accent);
    const color = new THREE.Color();

    for (let row = 0; row <= segments; row += 1) {
        for (let column = 0; column <= segments; column += 1) {
            const x = (column / segments - 0.5) * size;
            const z = (row / segments - 0.5) * size;
            const height = terrainHeightAtLocal(region, x, z);
            const index = row * (segments + 1) + column;
            const heightRatio = THREE.MathUtils.clamp((height + 1.5) / 3, 0, 1);
            const radial = THREE.MathUtils.clamp(Math.hypot(x, z) / (size * 0.5), 0, 1);

            positions[index * 3] = x;
            positions[index * 3 + 1] = height;
            positions[index * 3 + 2] = z;
            heights.push(height);

            color
                .copy(base)
                .lerp(edge, radial * 0.62)
                .lerp(accent, heightRatio * 0.18 + random() * 0.025);
            colors[index * 3] = color.r;
            colors[index * 3 + 1] = color.g;
            colors[index * 3 + 2] = color.b;
        }
    }

    for (let row = 0; row < segments; row += 1) {
        for (let column = 0; column < segments; column += 1) {
            const topLeft = row * (segments + 1) + column;
            const topRight = topLeft + 1;
            const bottomLeft = topLeft + segments + 1;
            const bottomRight = bottomLeft + 1;

            indices.push(topLeft, bottomLeft, topRight, topRight, bottomLeft, bottomRight);
        }
    }

    return {
        colors,
        heights,
        indices: new Uint32Array(indices),
        positions,
        segments,
        size,
    };
}
