import type {
    NatureKind,
    NaturePlan,
    PortalDefinition,
    RegionDefinition,
    RegionTheme,
    StructureKind,
    StructurePlan,
    Vec3,
    WaterPlan,
    WorldRegionId,
    WorldWeather,
} from "../types";
import { createSeededRandom, hashString, integerInRange, numberInRange, pickOne } from "./random";

type RegionTemplate = {
    accentVariants: readonly string[];
    description: string;
    id: WorldRegionId;
    name: string;
    natureKinds: readonly NatureKind[];
    structureKinds: readonly StructureKind[];
    subtitle: string;
    weather: WorldWeather;
};

const REGION_TEMPLATES: readonly RegionTemplate[] = [
    {
        accentVariants: ["#6D5DFC", "#7E70FF", "#5C73FF"],
        description: "所有梦境从这里醒来，第一束星光照亮脚下的大地。",
        id: "awakening",
        name: "初醒原野",
        natureKinds: ["grass", "flower", "tree", "crystal"],
        structureKinds: ["arch", "monument", "observatory"],
        subtitle: "DREAM ORIGIN",
        weather: "aurora",
    },
    {
        accentVariants: ["#56D9FF", "#39D0FF", "#7DE7FF"],
        description: "记忆沉入星海，每一颗星都在等待被重新看见。",
        id: "star-sea",
        name: "记忆星海",
        natureKinds: ["crystal", "grass", "rock"],
        structureKinds: ["observatory", "tower", "monument"],
        subtitle: "MEMORY TIDE",
        weather: "starfall",
    },
    {
        accentVariants: ["#B49BFF", "#FF8EC7", "#CFA8FF"],
        description: "被遗忘的线索在这里开花，灵感沿着藤蔓彼此靠近。",
        id: "inspiration-garden",
        name: "灵感花园",
        natureKinds: ["flower", "vine", "mushroom", "tree", "grass"],
        structureKinds: ["shrine", "arch", "monument"],
        subtitle: "INSPIRATION BLOOM",
        weather: "petals",
    },
    {
        accentVariants: ["#FFC857", "#F4A77B", "#FFD891"],
        description: "回声在断裂的石柱间往返，旧记忆以另一种方式继续发光。",
        id: "echo-ruins",
        name: "回声废墟",
        natureKinds: ["rock", "mushroom", "crystal", "grass"],
        structureKinds: ["ruin", "tower", "arch", "shrine"],
        subtitle: "ECHO DISTRICT",
        weather: "embers",
    },
    {
        accentVariants: ["#79E6B8", "#56D9FF", "#A4F3D1"],
        description: "梦境与外部世界交换光线，门户在雾潮中保持清醒。",
        id: "portal-harbor",
        name: "外部门户港",
        natureKinds: ["crystal", "grass", "rock", "vine"],
        structureKinds: ["tower", "arch", "building"],
        subtitle: "EXTERNAL HARBOR",
        weather: "mist",
    },
    {
        accentVariants: ["#FF9FD2", "#9C8CFF", "#FFC0E2"],
        description: "被反复阅读的记忆成为书页，风一吹便照亮新的叙事。",
        id: "dream-library",
        name: "梦境图书馆",
        natureKinds: ["vine", "flower", "tree", "grass"],
        structureKinds: ["library", "observatory", "shrine", "tower"],
        subtitle: "ARCHIVE OF DREAMS",
        weather: "clear",
    },
];

const REGION_CENTERS: Record<WorldRegionId, Vec3> = {
    awakening: [0, 0, 0],
    "star-sea": [0, 0, -96],
    "inspiration-garden": [84, 0, -48],
    "echo-ruins": [84, 0, 48],
    "portal-harbor": [0, 0, 96],
    "dream-library": [-84, 0, 48],
};

const ALTERNATE_LIBRARY_CENTER: Vec3 = [-84, 0, -48];

function themeForTemplate(template: RegionTemplate, seed: number): RegionTheme {
    const random = createSeededRandom(seed ^ hashString(`${template.id}:theme`));
    const accent = pickOne(random, template.accentVariants);
    const secondary = pickOne(
        random,
        template.accentVariants.filter((candidate) => candidate !== accent),
    );

    return {
        accent,
        ambient: secondary,
        fog: "#070a1b",
        ground: template.id === "echo-ruins" ? "#17120f" : "#090e23",
        groundEdge: "#03050f",
        secondary,
        sky: {
            horizon: template.id === "echo-ruins" ? "#2a1a28" : "#15123f",
            lower: "#0a0d27",
            top: "#040611",
        },
        star: "#F5F1FF",
    };
}

function structurePosition(center: Vec3, random: () => number, angle: number): Vec3 {
    const distance = numberInRange(random, 9, 24);

    return [center[0] + Math.cos(angle) * distance, 0, center[2] + Math.sin(angle) * distance];
}

function createStructures(template: RegionTemplate, center: Vec3, seed: number): StructurePlan[] {
    const random = createSeededRandom(seed ^ hashString(`${template.id}:structures`));
    const count = 9;

    return Array.from({ length: count }, (_, index) => {
        const angle = (index / count) * Math.PI * 2 + numberInRange(random, -0.2, 0.2);
        const heightScale = numberInRange(random, 0.75, 1.7);

        return {
            accent: pickOne(random, template.accentVariants),
            id: `${template.id}-structure-${index}`,
            kind: pickOne(random, template.structureKinds),
            position: structurePosition(center, random, angle),
            rotation: numberInRange(random, -Math.PI, Math.PI),
            scale: [numberInRange(random, 0.8, 1.35), heightScale, numberInRange(random, 0.8, 1.35)],
        };
    });
}

function createNature(template: RegionTemplate, center: Vec3, seed: number): NaturePlan[] {
    const random = createSeededRandom(seed ^ hashString(`${template.id}:nature`));
    const count = 54;

    return Array.from({ length: count }, (_, index) => {
        const angle = numberInRange(random, 0, Math.PI * 2);
        const distance = Math.sqrt(random()) * 31;

        return {
            accent: pickOne(random, template.accentVariants),
            id: `${template.id}-nature-${index}`,
            kind: pickOne(random, template.natureKinds),
            position: [center[0] + Math.cos(angle) * distance, 0, center[2] + Math.sin(angle) * distance],
            scale: numberInRange(random, 0.6, 1.9),
            variant: integerInRange(random, 0, 3),
        };
    });
}

function createWater(template: RegionTemplate, center: Vec3, seed: number): WaterPlan[] {
    const random = createSeededRandom(seed ^ hashString(`${template.id}:water`));
    const distance = numberInRange(random, 12, 18);
    const angle = numberInRange(random, 0, Math.PI * 2);
    const waterCount = template.id === "star-sea" || template.id === "inspiration-garden" ? 2 : 1;

    return Array.from({ length: waterCount }, (_, index) => ({
        accent: template.accentVariants[index % template.accentVariants.length],
        id: `${template.id}-water-${index}`,
        kind: index === 0 ? "lake" : "fountain",
        position: [
            center[0] + Math.cos(angle + index * 2.1) * distance,
            0,
            center[2] + Math.sin(angle + index * 2.1) * distance,
        ],
        scale: index === 0 ? [7.5, 0.08, 5.4] : [1.5, 1.2, 1.5],
    }));
}

function regionPortalPosition(center: Vec3, index: number, total: number): Vec3 {
    const angle = (index / total) * Math.PI * 2 - Math.PI / 2;
    const distance = 11.5;

    return [center[0] + Math.cos(angle) * distance, 0, center[2] + Math.sin(angle) * distance];
}

export function createRegion(
    id: WorldRegionId,
    seed: number,
    memoryIds: string[],
    externalPortals: readonly {
        accent: string;
        description: string;
        id: string;
        label: string;
        url: string;
    }[],
): RegionDefinition {
    const template = REGION_TEMPLATES.find((candidate) => candidate.id === id) ?? REGION_TEMPLATES[0];
    const center =
        id === "dream-library" ? (seed % 2 === 0 ? REGION_CENTERS[id] : ALTERNATE_LIBRARY_CENTER) : REGION_CENTERS[id];
    const random = createSeededRandom(seed ^ hashString(`${id}:region`));
    const theme = themeForTemplate(template, seed);
    const regionalPortals: PortalDefinition[] =
        id === "awakening"
            ? (
                  ["star-sea", "inspiration-garden", "echo-ruins", "portal-harbor", "dream-library"] as WorldRegionId[]
              ).map((targetRegionId, index, all) => ({
                  accent: theme.accent,
                  cooldownMs: 1000,
                  description: `穿过去往${targetRegionId ? regionDisplayName(targetRegionId) : ""}的梦境边缘`,
                  id: `portal-${id}-${targetRegionId}`,
                  kind: "region",
                  label: `前往${targetRegionId ? regionDisplayName(targetRegionId) : ""}`,
                  position: regionPortalPosition(center, index, all.length),
                  targetRegionId,
              }))
            : [
                  {
                      accent: theme.accent,
                      cooldownMs: 1000,
                      description: "回到梦境最初的入口",
                      id: `portal-${id}-awakening`,
                      kind: "region",
                      label: "返回初醒原野",
                      position: regionPortalPosition(center, 0, 5),
                      targetRegionId: "awakening",
                  },
              ];
    const harborPortals =
        id === "portal-harbor"
            ? externalPortals.map((portal, index) => ({
                  accent: portal.accent,
                  cooldownMs: 800,
                  description: portal.description,
                  externalUrl: portal.url,
                  id: portal.id,
                  kind: "external" as const,
                  label: portal.label,
                  position: regionPortalPosition(center, index + 1, externalPortals.length + 1),
              }))
            : [];

    return {
        ambientText: template.description,
        center,
        description: template.description,
        id,
        memoryIds,
        name: template.name,
        nature: createNature(template, center, seed),
        portals: [...regionalPortals, ...harborPortals],
        radius: 34,
        spawn: [center[0], 1.1, center[2] + 4],
        structures: createStructures(template, center, seed),
        subtitle: template.subtitle,
        terrain: {
            amplitude: numberInRange(random, 0.25, 1.15),
            frequency: numberInRange(random, 0.055, 0.11),
            roughness: numberInRange(random, 0.35, 1),
            seed: seed ^ hashString(`${id}:terrain`),
        },
        theme,
        water: createWater(template, center, seed),
        weather: template.weather,
    };
}

function regionDisplayName(id: WorldRegionId): string {
    return REGION_TEMPLATES.find((template) => template.id === id)?.name ?? id;
}

export function regionTemplateIds(): WorldRegionId[] {
    return REGION_TEMPLATES.map((template) => template.id);
}
