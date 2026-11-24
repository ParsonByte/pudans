"use client";

import * as THREE from "three";

import type { RegionTheme } from "../../types";

export function GroundMaterial({ theme }: { theme: RegionTheme }) {
    return (
        <meshStandardMaterial
            color={theme.ground}
            emissive={theme.accent}
            emissiveIntensity={0.055}
            metalness={0.26}
            roughness={0.72}
            toneMapped={false}
            vertexColors
        />
    );
}

export function groundGridColor(theme: RegionTheme): THREE.Color {
    return new THREE.Color(theme.accent).multiplyScalar(0.42);
}
