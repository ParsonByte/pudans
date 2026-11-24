"use client";

import { Suspense, useMemo } from "react";

import { useDreamStore } from "../../stores/use-dream-store";
import { filterMemories } from "../../world/memory-queries";
import { regionById } from "../../world/world-generator";
import { CameraRig } from "../camera/camera-rig";
import { CinematicCamera } from "../camera/cinematic-camera";
import { MouseLook } from "../camera/mouse-look";
import { ClickSelector } from "../interaction/click-selector";
import { InteractionRaycaster } from "../interaction/interaction-raycaster";
import { RegionManager } from "../world/region-manager";
import { WorldBoundary } from "./world-boundary";
import { WorldLoader } from "./world-loader";

export function SceneRouter() {
    const world = useDreamStore((state) => state.world);
    const activeRegionId = useDreamStore((state) => state.activeRegionId);
    const filters = useDreamStore((state) => state.filters);
    const reducedMotion = useDreamStore((state) => state.reducedMotion);
    const region = world ? regionById(world, activeRegionId) : null;
    const matchingMemories = useMemo(() => (world ? filterMemories(world.memories, filters) : []), [filters, world]);
    const matchedIds = useMemo(() => new Set(matchingMemories.map((memory) => memory.id)), [matchingMemories]);

    if (!world || !region) {
        return <WorldLoader label="正在生成属于你的天地" />;
    }

    return (
        <>
            <CameraRig reducedMotion={reducedMotion} />
            <MouseLook />
            <CinematicCamera />
            <ClickSelector />
            <InteractionRaycaster />
            <WorldBoundary />
            <Suspense fallback={null}>
                <RegionManager
                    key={region.id}
                    matchedIds={matchedIds}
                    matchingMemories={matchingMemories}
                    region={region}
                    world={world}
                />
            </Suspense>
        </>
    );
}
