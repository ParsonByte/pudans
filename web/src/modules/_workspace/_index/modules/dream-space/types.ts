export type MemoryType = "conversation" | "creation" | "insight" | "asset" | "project" | "person";

export type MemoryEmotion = "wonder" | "curiosity" | "calm" | "joy" | "resolve" | "melancholy";

export type Vec3 = [number, number, number];

export type Memory = {
    id: string;
    title: string;
    summary: string;
    type: MemoryType;
    tags: string[];
    emotion: MemoryEmotion;
    importance: number;
    createdAt: string;
    relations: string[];
};

export type MemoryLayout = Memory & {
    accent: string;
    groundPoint: Vec3;
    monthIndex: number;
    position: Vec3;
    regionId: WorldRegionId;
    timestamp: number;
};

export type WorldRegionId =
    "awakening" | "star-sea" | "inspiration-garden" | "echo-ruins" | "portal-harbor" | "dream-library";

export type WorldWeather = "aurora" | "clear" | "embers" | "mist" | "petals" | "starfall";

type SkyPalette = {
    horizon: string;
    lower: string;
    top: string;
};

export type RegionTheme = {
    accent: string;
    ambient: string;
    fog: string;
    ground: string;
    groundEdge: string;
    secondary: string;
    sky: SkyPalette;
    star: string;
};

type TerrainPlan = {
    amplitude: number;
    frequency: number;
    roughness: number;
    seed: number;
};

export type StructureKind = "arch" | "building" | "library" | "monument" | "observatory" | "ruin" | "shrine" | "tower";

export type StructurePlan = {
    accent: string;
    id: string;
    kind: StructureKind;
    position: Vec3;
    rotation: number;
    scale: Vec3;
};

export type NatureKind = "crystal" | "flower" | "grass" | "mushroom" | "rock" | "tree" | "vine";

export type NaturePlan = {
    accent: string;
    id: string;
    kind: NatureKind;
    position: Vec3;
    scale: number;
    variant: number;
};

export type WaterPlan = {
    accent: string;
    id: string;
    kind: "fountain" | "lake" | "river" | "waterfall";
    position: Vec3;
    scale: Vec3;
};

export type PortalKind = "external" | "region";

export type PortalDefinition = {
    accent: string;
    cooldownMs: number;
    description: string;
    externalUrl?: string;
    id: string;
    kind: PortalKind;
    label: string;
    position: Vec3;
    targetRegionId?: WorldRegionId;
};

export type RegionDefinition = {
    ambientText: string;
    center: Vec3;
    description: string;
    id: WorldRegionId;
    memoryIds: string[];
    name: string;
    nature: NaturePlan[];
    portals: PortalDefinition[];
    radius: number;
    spawn: Vec3;
    structures: StructurePlan[];
    subtitle: string;
    terrain: TerrainPlan;
    theme: RegionTheme;
    water: WaterPlan[];
    weather: WorldWeather;
};

export type WorldDefinition = {
    createdAt: string;
    memories: MemoryLayout[];
    regions: RegionDefinition[];
    seed: number;
    sessionId: string;
    shortCode: string;
    userId: string;
    version: number;
};

export type WorldSnapshot = {
    activeRegionId: WorldRegionId;
    discoveredMemoryIds: string[];
    inspiration: InspirationCard | null;
    inspirationIds: string[];
    inspirationRound: number;
    openedPortalIds: string[];
    playerPosition: Vec3;
    savedAt: string;
    seed: number;
    sessionId: string;
    userId: string;
    visitedRegionIds: WorldRegionId[];
};

export type DreamMode = "dream" | "inspiration" | "reality";

export type DreamFilters = {
    emotions: MemoryEmotion[];
    minImportance: number;
    query: string;
    tags: string[];
    timeRange: [number, number];
    types: MemoryType[];
};

export type InspirationCard = {
    id: string;
    keywords: string[];
    memoryIds: string[];
    prompt: string;
    saved: boolean;
    title: string;
};

export type PlayerPose = {
    grounded: boolean;
    pitch: number;
    position: Vec3;
    stamina: number;
    yaw: number;
};

export type CameraMode = "first" | "third";

export type DreamPanel = "details" | "filters" | "inspiration" | "journal" | "map" | "settings" | "story" | null;

export type WorldPhase = "fallback" | "loading" | "paused" | "ready" | "transition";

type InteractionTargetKind = "memory" | "npc" | "portal" | "relic";

export type InteractionTarget = {
    id: string;
    kind: InteractionTargetKind;
    label: string;
    position: Vec3;
    subtitle: string;
};

export type ToastTone = "info" | "success" | "warning";

export type ToastMessage = {
    id: string;
    message: string;
    tone: ToastTone;
};

export type DiscoveryEntry = {
    id: string;
    label: string;
    regionId: WorldRegionId;
    timestamp: string;
};

export type DreamMemoryMeta = {
    color: string;
    label: string;
    shape: string;
};

export type WorldEventListener = (event: WorldEvent) => void;

export type WorldEvent =
    | { type: "portal-opened"; portalId: string }
    | { type: "memory-discovered"; memoryId: string }
    | { type: "region-entered"; regionId: WorldRegionId }
    | { type: "inspiration-created"; inspirationId: string };
