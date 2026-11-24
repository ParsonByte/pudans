import { MEMORY_EMOTION_META, TIMELINE_END } from "../constants";
import { MOCK_MEMORIES } from "../data/mock-memories";
import type { Memory, MemoryLayout, RegionDefinition, Vec3 } from "../types";
import { createSeededRandom, hashString, numberInRange } from "./random";

function memoryMonthIndex(createdAt: string): number {
    const date = new Date(createdAt);
    const monthIndex = (date.getFullYear() - 2024) * 12 + date.getMonth() - 11;

    return Math.max(0, Math.min(TIMELINE_END, monthIndex));
}

export function allocateMemories(seed: number): Map<string, string[]> {
    const random = createSeededRandom(seed ^ 0x5eeda11);
    const shuffled = [...MOCK_MEMORIES];
    const result = new Map<string, string[]>();
    const regionIds = ["awakening", "star-sea", "inspiration-garden", "echo-ruins", "portal-harbor", "dream-library"];
    const budgets = [5, 6, 6, 6, 5, 6];

    for (let index = shuffled.length - 1; index > 0; index -= 1) {
        const target = Math.floor(random() * (index + 1));
        [shuffled[index], shuffled[target]] = [shuffled[target], shuffled[index]];
    }

    let cursor = 0;

    regionIds.forEach((regionId, index) => {
        const budget = index === regionIds.length - 1 ? shuffled.length - cursor : budgets[index];
        const ids = shuffled.slice(cursor, cursor + Math.max(0, budget)).map((memory) => memory.id);
        result.set(regionId, ids);
        cursor += budget;
    });

    return result;
}

function memoryPosition(region: RegionDefinition, memory: Memory, seed: number): { ground: Vec3; position: Vec3 } {
    const random = createSeededRandom(seed ^ hashString(memory.id));
    const angle = numberInRange(random, 0, Math.PI * 2);
    const radius = numberInRange(random, 8, 22);
    const height = 8 + memory.importance * 13 + numberInRange(random, 0, 4);
    const x = region.center[0] + Math.cos(angle) * radius;
    const z = region.center[2] + Math.sin(angle) * radius;

    return {
        ground: [x, 0.08, z],
        position: [x, height, z],
    };
}

export function createMemoryLayouts(
    regions: RegionDefinition[],
    memoryIdsByRegion: Map<string, string[]>,
    seed: number,
): MemoryLayout[] {
    const memoryById = new Map(MOCK_MEMORIES.map((memory) => [memory.id, memory]));
    const layouts: MemoryLayout[] = [];

    regions.forEach((region) => {
        const ids = memoryIdsByRegion.get(region.id) ?? [];

        ids.forEach((memoryId) => {
            const memory = memoryById.get(memoryId);

            if (!memory) {
                return;
            }

            const position = memoryPosition(region, memory, seed);

            layouts.push({
                ...memory,
                accent: MEMORY_EMOTION_META[memory.emotion].color,
                groundPoint: position.ground,
                monthIndex: memoryMonthIndex(memory.createdAt),
                position: position.position,
                regionId: region.id,
                timestamp: new Date(memory.createdAt).getTime(),
            });
        });
    });

    return layouts.sort((left, right) => left.timestamp - right.timestamp);
}
