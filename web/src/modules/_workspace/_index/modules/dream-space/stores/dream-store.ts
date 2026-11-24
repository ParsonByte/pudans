import { createStore } from "@/stores/create-store";

import { SNAPSHOT_STORAGE_PREFIX, TIMELINE_END, TIMELINE_START } from "../constants";
import type {
    CameraMode,
    DiscoveryEntry,
    DreamFilters,
    DreamMode,
    DreamPanel,
    InspirationCard,
    InteractionTarget,
    MemoryEmotion,
    MemoryType,
    PlayerPose,
    ToastMessage,
    ToastTone,
    Vec3,
    WorldDefinition,
    WorldPhase,
    WorldRegionId,
    WorldSnapshot,
} from "../types";
import { clampTimelineRange } from "../world/memory-queries";
import { generateWorld, regionById } from "../world/world-generator";

export type DreamStoreState = {
    activeRegionId: WorldRegionId;
    audioEnabled: boolean;
    cameraMode: CameraMode;
    debugVisible: boolean;
    directoryOpen: boolean;
    discoveredMemoryIds: string[];
    discoveryLog: DiscoveryEntry[];
    filters: DreamFilters;
    hoveredMemoryId: string | null;
    highContrast: boolean;
    hudVisible: boolean;
    inspiration: InspirationCard | null;
    inspirationRound: number;
    interactionTarget: InteractionTarget | null;
    mode: DreamMode;
    openedPortalIds: string[];
    panel: DreamPanel;
    paused: boolean;
    performanceMode: boolean;
    phase: WorldPhase;
    playerPose: PlayerPose;
    previewPortalId: string | null;
    reducedMotion: boolean;
    resultOnly: boolean;
    selectedMemoryId: string | null;
    toasts: ToastMessage[];
    transitionLabel: string;
    visitedRegionIds: WorldRegionId[];
    world: WorldDefinition | null;
};

const INITIAL_FILTERS: DreamFilters = {
    emotions: [],
    minImportance: 0,
    query: "",
    tags: [],
    timeRange: [TIMELINE_START, TIMELINE_END],
    types: [],
};

const INITIAL_PLAYER_POSE: PlayerPose = {
    grounded: true,
    pitch: -0.1,
    position: [0, 1.1, 4],
    stamina: 1,
    yaw: Math.PI,
};

export const dreamStore = createStore<DreamStoreState>({
    activeRegionId: "awakening",
    audioEnabled: false,
    cameraMode: "third",
    debugVisible: false,
    directoryOpen: false,
    discoveredMemoryIds: [],
    discoveryLog: [],
    filters: INITIAL_FILTERS,
    hoveredMemoryId: null,
    highContrast: false,
    hudVisible: true,
    inspiration: null,
    inspirationRound: 0,
    interactionTarget: null,
    mode: "dream",
    openedPortalIds: [],
    panel: null,
    paused: false,
    performanceMode: false,
    phase: "loading",
    playerPose: INITIAL_PLAYER_POSE,
    previewPortalId: null,
    reducedMotion: false,
    resultOnly: false,
    selectedMemoryId: null,
    toasts: [],
    transitionLabel: "正在校准梦境",
    visitedRegionIds: ["awakening"],
    world: null,
});

function snapshotKey(world: WorldDefinition): string {
    return `${SNAPSHOT_STORAGE_PREFIX}:${world.userId}:${world.sessionId}:${world.version}`;
}

function loadSnapshot(world: WorldDefinition): WorldSnapshot | null {
    if (typeof window === "undefined") {
        return null;
    }

    try {
        const raw = window.localStorage.getItem(snapshotKey(world));

        if (!raw) {
            return null;
        }

        const snapshot = JSON.parse(raw) as WorldSnapshot;

        if (
            snapshot.seed !== world.seed ||
            snapshot.sessionId !== world.sessionId ||
            snapshot.userId !== world.userId
        ) {
            return null;
        }

        return snapshot;
    } catch {
        return null;
    }
}

export function initializeDreamWorld(userId: string, sessionId: string): void {
    const world = generateWorld(userId, sessionId);
    const snapshot = loadSnapshot(world);
    const activeRegionId = snapshot?.activeRegionId ?? "awakening";
    const region = regionById(world, activeRegionId);
    const inspiration = snapshot?.inspiration ?? null;
    const discoveredMemoryIds = snapshot?.discoveredMemoryIds ?? [];
    const discoveryLog = world.memories
        .filter((memory) => discoveredMemoryIds.includes(memory.id))
        .map((memory) => ({
            id: memory.id,
            label: memory.title,
            regionId: memory.regionId,
            timestamp: memory.createdAt,
        }));

    dreamStore.setState((state) => ({
        ...state,
        activeRegionId,
        discoveredMemoryIds,
        discoveryLog,
        inspiration,
        inspirationRound: snapshot?.inspirationRound ?? 0,
        openedPortalIds: snapshot?.openedPortalIds ?? [],
        phase: "ready",
        playerPose: snapshot
            ? { ...INITIAL_PLAYER_POSE, position: snapshot.playerPosition }
            : { ...INITIAL_PLAYER_POSE, position: region.spawn },
        visitedRegionIds: snapshot?.visitedRegionIds.length ? snapshot.visitedRegionIds : [activeRegionId],
        world,
    }));
}

export function setDreamPhase(phase: WorldPhase): void {
    dreamStore.setState((state) => ({ ...state, phase }));
}

export function enterDreamRegion(regionId: WorldRegionId, nextPhase: WorldPhase = "ready"): void {
    dreamStore.setState((state) => {
        if (!state.world) {
            return state;
        }

        const region = regionById(state.world, regionId);
        const visitedRegionIds = state.visitedRegionIds.includes(regionId)
            ? state.visitedRegionIds
            : [...state.visitedRegionIds, regionId];

        return {
            ...state,
            activeRegionId: regionId,
            interactionTarget: null,
            panel: null,
            phase: nextPhase,
            playerPose: { ...state.playerPose, grounded: true, position: region.spawn },
            selectedMemoryId: null,
            visitedRegionIds,
        };
    });
}

export function beginDreamTransition(label: string): void {
    dreamStore.setState((state) => ({
        ...state,
        panel: null,
        phase: "transition",
        transitionLabel: label,
    }));
}

export function setDreamMode(mode: DreamMode): void {
    dreamStore.setState((state) => ({ ...state, mode }));
}

export function setDreamCameraMode(cameraMode: CameraMode): void {
    dreamStore.setState((state) => ({ ...state, cameraMode }));
}

export function setDreamPanel(panel: DreamPanel): void {
    dreamStore.setState((state) => ({ ...state, panel }));
}

export function setDreamPreviewPortal(previewPortalId: string | null): void {
    dreamStore.setState((state) => ({ ...state, previewPortalId }));
}

export function toggleDreamPanel(panel: Exclude<DreamPanel, null>): void {
    dreamStore.setState((state) => ({ ...state, panel: state.panel === panel ? null : panel }));
}

export function setDreamDirectoryOpen(directoryOpen: boolean): void {
    dreamStore.setState((state) => ({ ...state, directoryOpen }));
}

export function setDreamHudVisible(hudVisible: boolean): void {
    dreamStore.setState((state) => ({ ...state, hudVisible }));
}

export function setDreamPaused(paused: boolean): void {
    dreamStore.setState((state) => ({ ...state, paused }));
}

export function setDreamQuery(query: string): void {
    dreamStore.setState((state) => ({
        ...state,
        filters: { ...state.filters, query },
    }));
}

function toggleValue<T>(items: T[], value: T): T[] {
    return items.includes(value) ? items.filter((item) => item !== value) : [...items, value];
}

export function toggleDreamType(type: MemoryType): void {
    dreamStore.setState((state) => ({
        ...state,
        filters: { ...state.filters, types: toggleValue(state.filters.types, type) },
    }));
}

export function toggleDreamEmotion(emotion: MemoryEmotion): void {
    dreamStore.setState((state) => ({
        ...state,
        filters: { ...state.filters, emotions: toggleValue(state.filters.emotions, emotion) },
    }));
}

export function toggleDreamTag(tag: string): void {
    dreamStore.setState((state) => ({
        ...state,
        filters: { ...state.filters, tags: toggleValue(state.filters.tags, tag) },
    }));
}

export function setDreamImportance(minImportance: number): void {
    dreamStore.setState((state) => ({
        ...state,
        filters: { ...state.filters, minImportance },
    }));
}

export function setDreamTimeRange(timeRange: [number, number]): void {
    dreamStore.setState((state) => ({
        ...state,
        filters: { ...state.filters, timeRange: clampTimelineRange(timeRange) },
    }));
}

export function resetDreamFilters(): void {
    dreamStore.setState((state) => ({
        ...state,
        filters: INITIAL_FILTERS,
        resultOnly: false,
    }));
}

export function setDreamResultOnly(resultOnly: boolean): void {
    dreamStore.setState((state) => ({ ...state, resultOnly }));
}

export function selectDreamMemory(memoryId: string | null): void {
    dreamStore.setState((state) => {
        if (!memoryId) {
            return { ...state, panel: null, selectedMemoryId: null };
        }

        const memory = state.world?.memories.find((candidate) => candidate.id === memoryId);

        if (!memory) {
            return state;
        }

        const alreadyDiscovered = state.discoveredMemoryIds.includes(memoryId);
        const discoveryLog = alreadyDiscovered
            ? state.discoveryLog
            : [
                  {
                      id: memory.id,
                      label: memory.title,
                      regionId: memory.regionId,
                      timestamp: new Date().toISOString(),
                  },
                  ...state.discoveryLog,
              ];

        return {
            ...state,
            discoveredMemoryIds: alreadyDiscovered
                ? state.discoveredMemoryIds
                : [...state.discoveredMemoryIds, memoryId],
            discoveryLog,
            panel: "details",
            selectedMemoryId: memoryId,
        };
    });
}

export function setDreamHoveredMemory(hoveredMemoryId: string | null): void {
    dreamStore.setState((state) => (state.hoveredMemoryId === hoveredMemoryId ? state : { ...state, hoveredMemoryId }));
}

export function setDreamInteractionTarget(interactionTarget: InteractionTarget | null): void {
    dreamStore.setState((state) => {
        if (state.interactionTarget?.id === interactionTarget?.id) {
            return state;
        }

        return { ...state, interactionTarget };
    });
}

export function setDreamPlayerPose(pose: Partial<PlayerPose>): void {
    dreamStore.setState((state) => ({ ...state, playerPose: { ...state.playerPose, ...pose } }));
}

export function setDreamPlayerPosition(position: Vec3): void {
    dreamStore.setState((state) => ({
        ...state,
        playerPose: { ...state.playerPose, position },
    }));
}

export function setDreamReducedMotion(reducedMotion: boolean): void {
    dreamStore.setState((state) => ({ ...state, reducedMotion }));
}

export function setDreamPerformanceMode(performanceMode: boolean): void {
    dreamStore.setState((state) => ({ ...state, performanceMode }));
}

export function setDreamAudioEnabled(audioEnabled: boolean): void {
    dreamStore.setState((state) => ({ ...state, audioEnabled }));
}

export function setDreamDebugVisible(debugVisible: boolean): void {
    dreamStore.setState((state) => ({ ...state, debugVisible }));
}

export function setDreamHighContrast(highContrast: boolean): void {
    dreamStore.setState((state) => ({ ...state, highContrast }));
}

export function setDreamInspiration(inspiration: InspirationCard): void {
    dreamStore.setState((state) => ({
        ...state,
        inspiration,
        inspirationRound: state.inspirationRound + 1,
        mode: "inspiration",
        panel: "inspiration",
    }));
}

export function toggleDreamInspirationSaved(): void {
    dreamStore.setState((state) =>
        state.inspiration
            ? {
                  ...state,
                  inspiration: { ...state.inspiration, saved: !state.inspiration.saved },
              }
            : state,
    );
}

export function pushDreamToast(message: string, tone: ToastTone = "info"): void {
    dreamStore.setState((state) => ({
        ...state,
        toasts: [...state.toasts, { id: `toast-${Date.now()}-${state.toasts.length}`, message, tone }].slice(-3),
    }));
}

export function dismissDreamToast(id: string): void {
    dreamStore.setState((state) => ({
        ...state,
        toasts: state.toasts.filter((toast) => toast.id !== id),
    }));
}

export function resetDreamWorldPosition(): void {
    dreamStore.setState((state) => {
        if (!state.world) {
            return state;
        }

        const region = regionById(state.world, state.activeRegionId);

        return {
            ...state,
            playerPose: { ...state.playerPose, grounded: true, position: region.spawn },
        };
    });
}

export function setDreamOpenedPortal(portalId: string): void {
    dreamStore.setState((state) => ({
        ...state,
        openedPortalIds: state.openedPortalIds.includes(portalId)
            ? state.openedPortalIds
            : [...state.openedPortalIds, portalId],
    }));
}

function snapshotDreamWorld(state: DreamStoreState): WorldSnapshot | null {
    if (!state.world) {
        return null;
    }

    return {
        activeRegionId: state.activeRegionId,
        discoveredMemoryIds: state.discoveredMemoryIds,
        inspiration: state.inspiration,
        inspirationIds: state.inspiration?.memoryIds ?? [],
        inspirationRound: state.inspirationRound,
        openedPortalIds: state.openedPortalIds,
        playerPosition: state.playerPose.position,
        savedAt: new Date().toISOString(),
        seed: state.world.seed,
        sessionId: state.world.sessionId,
        userId: state.world.userId,
        visitedRegionIds: state.visitedRegionIds,
    };
}

export function persistDreamSnapshot(): void {
    if (typeof window === "undefined") {
        return;
    }

    const state = dreamStore.getState();
    const snapshot = snapshotDreamWorld(state);

    if (!snapshot || !state.world) {
        return;
    }

    try {
        window.localStorage.setItem(snapshotKey(state.world), JSON.stringify(snapshot));
    } catch {
        // 存储不可用时，世界仍保持内存态可玩。
    }
}

export function startNewDreamSession(sessionId: string, userId: string): void {
    const world = generateWorld(userId, sessionId);
    const region = regionById(world, "awakening");

    dreamStore.setState((state) => ({
        ...state,
        activeRegionId: "awakening",
        discoveredMemoryIds: [],
        discoveryLog: [],
        inspiration: null,
        openedPortalIds: [],
        phase: "ready",
        playerPose: { ...INITIAL_PLAYER_POSE, position: region.spawn },
        selectedMemoryId: null,
        visitedRegionIds: ["awakening"],
        world,
    }));
}
