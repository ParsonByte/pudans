import { TIMELINE_END, TIMELINE_MONTHS } from "../constants";
import type { DreamFilters, InspirationCard, MemoryLayout } from "../types";

export function monthRangeLabel(range: [number, number]): string {
    return `${TIMELINE_MONTHS[range[0]]} — ${TIMELINE_MONTHS[range[1]]}`;
}

export function formatMemoryDate(createdAt: string): string {
    return new Intl.DateTimeFormat("zh-CN", {
        day: "2-digit",
        month: "long",
        year: "numeric",
    }).format(new Date(createdAt));
}

export function formatMemoryTime(createdAt: string): string {
    return new Intl.DateTimeFormat("zh-CN", {
        hour: "2-digit",
        minute: "2-digit",
    }).format(new Date(createdAt));
}

export function filterMemories(memories: MemoryLayout[], filters: DreamFilters): MemoryLayout[] {
    const query = filters.query.trim().toLocaleLowerCase("zh-CN");

    return memories.filter((memory) => {
        if (filters.types.length > 0 && !filters.types.includes(memory.type)) {
            return false;
        }

        if (filters.emotions.length > 0 && !filters.emotions.includes(memory.emotion)) {
            return false;
        }

        if (filters.tags.length > 0 && !filters.tags.some((tag) => memory.tags.includes(tag))) {
            return false;
        }

        if (memory.importance < filters.minImportance) {
            return false;
        }

        if (memory.monthIndex < filters.timeRange[0] || memory.monthIndex > filters.timeRange[1]) {
            return false;
        }

        if (!query) {
            return true;
        }

        return [memory.title, memory.summary, ...memory.tags].some((value) =>
            value.toLocaleLowerCase("zh-CN").includes(query),
        );
    });
}

export function collectMemoryTags(memories: MemoryLayout[], limit = 12): string[] {
    const counts = new Map<string, number>();

    memories.forEach((memory) => {
        memory.tags.forEach((tag) => counts.set(tag, (counts.get(tag) ?? 0) + 1));
    });

    return [...counts.entries()]
        .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0], "zh-CN"))
        .slice(0, limit)
        .map(([tag]) => tag);
}

export function createInspiration(memories: MemoryLayout[], selectedId: string | null, seed: number): InspirationCard {
    const candidates = memories.length > 0 ? memories : [];
    const byId = new Map(candidates.map((memory) => [memory.id, memory]));
    const selected = selectedId ? byId.get(selectedId) : undefined;
    const anchor = selected ?? candidates[seed % Math.max(1, candidates.length)];

    if (!anchor) {
        return {
            id: "inspiration-empty",
            keywords: [],
            memoryIds: [],
            prompt: "当前筛选条件下没有可重组的记忆。",
            saved: false,
            title: "星图暂时保持安静",
        };
    }

    const group: MemoryLayout[] = [anchor];
    const visited = new Set([anchor.id]);
    const queue = [...anchor.relations];

    while (queue.length > 0 && group.length < 4) {
        const nextId = queue.shift();

        if (!nextId || visited.has(nextId)) {
            continue;
        }

        const next = byId.get(nextId) ?? candidates.find((memory) => memory.id === nextId);

        if (!next) {
            continue;
        }

        visited.add(next.id);
        group.push(next);
        queue.push(...next.relations);
    }

    for (const memory of candidates) {
        if (group.length >= 4) {
            break;
        }

        if (!visited.has(memory.id)) {
            visited.add(memory.id);
            group.push(memory);
        }
    }

    const keywordCounts = new Map<string, number>();

    group.forEach((memory) => {
        memory.tags.forEach((tag) => keywordCounts.set(tag, (keywordCounts.get(tag) ?? 0) + 1));
    });

    const keywords = [...keywordCounts.entries()]
        .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0], "zh-CN"))
        .slice(0, 3)
        .map(([keyword]) => keyword);
    const memoryIds = group.map((memory) => memory.id);
    const keywordLine = keywords.length > 0 ? keywords.join("、") : "记忆、光线与情绪";
    const relatedTitles = group
        .slice(1)
        .map((memory) => `「${memory.title}」`)
        .join("、");

    return {
        id: `inspiration-${seed}-${memoryIds.join("-")}`,
        keywords,
        memoryIds,
        prompt: `把${relatedTitles || "这些记忆"}与「${anchor.title}」放在同一条叙事线上，围绕${keywordLine}生成一个兼具空间感与情绪张力的创作方向。`,
        saved: false,
        title: `让「${anchor.title}」与${keywordLine}重新相遇`,
    };
}

export function clampTimelineRange(range: [number, number]): [number, number] {
    return [Math.max(0, Math.min(TIMELINE_END, range[0])), Math.max(0, Math.min(TIMELINE_END, range[1]))];
}
