"use client";

import { useRef } from "react";

import { useFrame } from "@react-three/fiber";
import type * as THREE from "three";

import type { RegionDefinition } from "../../types";

export function LightningFlash({ region, reducedMotion }: { region: RegionDefinition; reducedMotion: boolean }) {
    const light = useRef<THREE.PointLight>(null);

    useFrame(({ clock }) => {
        if (!light.current) {
            return;
        }

        const phase = (clock.elapsedTime * 0.09 + region.terrain.seed * 0.00001) % 1;
        const flash = reducedMotion ? 0 : phase > 0.972 ? Math.max(0, 1 - (phase - 0.972) / 0.028) : 0;
        light.current.intensity = flash * 34;
    });

    return (
        <pointLight
            color={region.theme.secondary}
            distance={90}
            intensity={0}
            position={[region.center[0], 28, region.center[2] - 12]}
            ref={light}
        />
    );
}
