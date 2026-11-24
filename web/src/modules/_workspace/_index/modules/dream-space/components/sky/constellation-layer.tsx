"use client";

import { useMemo } from "react";

import * as THREE from "three";

import type { MemoryLayout, RegionDefinition } from "../../types";

export function ConstellationLayer({
    memories,
    region,
    selectedId,
}: {
    memories: MemoryLayout[];
    region: RegionDefinition;
    selectedId: string | null;
}) {
    const geometry = useMemo(() => {
        const byId = new Map(memories.map((memory) => [memory.id, memory]));
        const positions: number[] = [];
        const colors: number[] = [];
        const selectedColor = new THREE.Color(region.theme.accent);
        const normalColor = new THREE.Color(region.theme.secondary);

        memories.forEach((memory) => {
            memory.relations.forEach((relationId) => {
                const relation = byId.get(relationId);

                if (!relation || relation.id < memory.id) {
                    return;
                }

                positions.push(...memory.position, ...relation.position);
                const color = memory.id === selectedId || relation.id === selectedId ? selectedColor : normalColor;
                colors.push(color.r, color.g, color.b, color.r, color.g, color.b);
            });
        });

        const nextGeometry = new THREE.BufferGeometry();
        nextGeometry.setAttribute("position", new THREE.Float32BufferAttribute(positions, 3));
        nextGeometry.setAttribute("color", new THREE.Float32BufferAttribute(colors, 3));

        return nextGeometry;
    }, [memories, region.theme.accent, region.theme.secondary, selectedId]);

    return (
        <lineSegments geometry={geometry} renderOrder={2}>
            <lineBasicMaterial
                blending={THREE.AdditiveBlending}
                depthWrite={false}
                opacity={selectedId ? 0.62 : 0.22}
                toneMapped={false}
                transparent
                vertexColors
            />
        </lineSegments>
    );
}
