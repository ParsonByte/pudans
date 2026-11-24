"use client";

import { useMemo } from "react";

import * as THREE from "three";

import type { MemoryLayout } from "../../types";

export function MemoryTrail({ memory }: { memory: MemoryLayout }) {
    const { geometry, material } = useMemo(() => {
        const segmentCount = 18;
        const points = Array.from({ length: segmentCount + 1 }, (_, index) => {
            const ratio = index / segmentCount;
            const x = THREE.MathUtils.lerp(memory.position[0], memory.groundPoint[0], ratio);
            const z = THREE.MathUtils.lerp(memory.position[2], memory.groundPoint[2], ratio);
            const y = THREE.MathUtils.lerp(memory.position[1], memory.groundPoint[1], ratio);
            const wave = Math.sin(ratio * Math.PI * 4) * 0.2 * (1 - ratio);

            return new THREE.Vector3(x + wave, y, z + wave * 0.4);
        });
        const curve = new THREE.CatmullRomCurve3(points);
        const nextGeometry = new THREE.TubeGeometry(curve, 28, 0.012, 4, false);
        const nextMaterial = new THREE.MeshBasicMaterial({
            blending: THREE.AdditiveBlending,
            color: memory.accent,
            depthWrite: false,
            opacity: 0.34,
            transparent: true,
        });

        return { geometry: nextGeometry, material: nextMaterial };
    }, [memory]);

    return <mesh geometry={geometry} material={material} />;
}
