import { WORLD_VERSION } from "../constants";
import type { WorldDefinition, WorldRegionId } from "../types";
import { allocateMemories, createMemoryLayouts } from "./memory-layout";
import { portalRegistry } from "./portal-registry";
import { createRegion, regionTemplateIds } from "./region-generator";
import { createWorldSeed } from "./seed";

export function generateWorld(userId: string, sessionId: string, version = WORLD_VERSION): WorldDefinition {
    const worldSeed = createWorldSeed(userId, sessionId, version);
    const memoryIdsByRegion = allocateMemories(worldSeed.seed);
    const externalPortals = portalRegistry.list();
    const regions = regionTemplateIds().map((id) =>
        createRegion(id, worldSeed.seed, memoryIdsByRegion.get(id) ?? [], externalPortals),
    );
    const memories = createMemoryLayouts(regions, memoryIdsByRegion, worldSeed.seed);

    return {
        createdAt: new Date().toISOString(),
        memories,
        regions,
        seed: worldSeed.seed,
        sessionId,
        shortCode: worldSeed.shortCode,
        userId,
        version,
    };
}

export function regionById(world: WorldDefinition, regionId: WorldRegionId) {
    return world.regions.find((region) => region.id === regionId) ?? world.regions[0];
}

export function memoriesForRegion(world: WorldDefinition, regionId: WorldRegionId) {
    return world.memories.filter((memory) => memory.regionId === regionId);
}
