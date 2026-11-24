"use client";

import { useMemo } from "react";

import { selectDreamMemory } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import type { MemoryLayout, RegionDefinition, StructurePlan, WorldDefinition } from "../../types";
import { memoriesForRegion } from "../../world/world-generator";
import { ArchGate } from "../architecture/arch-gate";
import { BridgePortal } from "../architecture/bridge-portal";
import { DreamBuilding } from "../architecture/dream-building";
import { FloatingIsland } from "../architecture/floating-island";
import { LibraryTower } from "../architecture/library-tower";
import { MemoryMonument } from "../architecture/memory-monument";
import { Observatory } from "../architecture/observatory";
import { ProceduralCityBlock } from "../architecture/procedural-city-block";
import { RuinTower } from "../architecture/ruin-tower";
import { TempleShrine } from "../architecture/temple-shrine";
import { DreamFogParticles } from "../fx/dream-fog-particles";
import { GlowAura } from "../fx/glow-aura";
import { StarDustParticles } from "../fx/star-dust-particles";
import { CreatureFlock } from "../interaction/creature-flock";
import { MemoryPickup } from "../interaction/memory-pickup";
import { NPCQuestGiver } from "../interaction/npc-quest-giver";
import { ProximityPrompt } from "../interaction/proximity-prompt";
import { MemoryStarfield } from "../memory/memory-starfield";
import { NatureLayer } from "../nature/nature-layer";
import { FootstepController } from "../player/footstep-controller";
import { PlayerAvatar } from "../player/player-avatar";
import { PlayerMovement } from "../player/player-movement";
import { PlayerShadow } from "../player/player-shadow";
import { RunStamina } from "../player/run-stamina";
import { PortalGate } from "../portals/portal-gate";
import { AuroraCurtain } from "../sky/aurora-curtain";
import { ConstellationLayer } from "../sky/constellation-layer";
import { DreamSkyDome } from "../sky/dream-sky-dome";
import { MeteorShower } from "../sky/meteor-shower";
import { MoonOrb } from "../sky/moon-orb";
import { NebulaCloud } from "../sky/nebula-cloud";
import { StarField } from "../sky/star-field";
import { StarTwinkleController } from "../sky/star-twinkle-controller";
import { SunHalo } from "../sky/sun-halo";
import { Bridge } from "../terrain/bridge";
import { Cliff } from "../terrain/cliff";
import { GroundDecal } from "../terrain/ground-decal";
import { GroundMesh } from "../terrain/ground-mesh";
import { HeightfieldCollider } from "../terrain/heightfield-collider";
import { PathNetwork } from "../terrain/path-network";
import { Stair } from "../terrain/stair";
import { TerrainGenerator } from "../terrain/terrain-generator";
import { WaterLayer } from "../water/water-layer";
import { WeatherAudioBridge } from "../weather/weather-audio-bridge";
import { WeatherController } from "../weather/weather-controller";
import { InstancedWorldObjects } from "./instanced-world-objects";
import { RegionAmbience } from "./region-ambience";
import { RegionBoundary } from "./region-boundary";
import { RegionFogController } from "./region-fog-controller";
import { RegionLoader } from "./region-loader";
import { RegionSpawnPoint } from "./region-spawn-point";
import { RegionThemeProvider } from "./region-theme-provider";
import { WorldObjectPool } from "./world-object-pool";

const EMPTY_IDS: string[] = [];

function StructureNode({ plan }: { plan: StructurePlan }) {
    if (plan.kind === "arch") {
        return <ArchGate plan={plan} />;
    }

    if (plan.kind === "library") {
        return <LibraryTower plan={plan} />;
    }

    if (plan.kind === "monument") {
        return <MemoryMonument plan={plan} />;
    }

    if (plan.kind === "observatory") {
        return <Observatory plan={plan} />;
    }

    if (plan.kind === "ruin") {
        return <RuinTower plan={plan} />;
    }

    if (plan.kind === "shrine") {
        return <TempleShrine plan={plan} />;
    }

    if (plan.kind === "tower") {
        return <BridgePortal plan={plan} />;
    }

    return <DreamBuilding plan={plan} />;
}

export function RegionManager({
    matchedIds,
    matchingMemories,
    region,
    world,
}: {
    matchedIds: Set<string>;
    matchingMemories: MemoryLayout[];
    region: RegionDefinition;
    world: WorldDefinition;
}) {
    const selectedMemoryId = useDreamStore((state) => state.selectedMemoryId);
    const inspiration = useDreamStore((state) => state.inspiration);
    const inspirationIds = inspiration?.memoryIds;
    const resultOnly = useDreamStore((state) => state.resultOnly);
    const reducedMotion = useDreamStore((state) => state.reducedMotion);
    const performanceMode = useDreamStore((state) => state.performanceMode);
    const regionMemories = useMemo(() => memoriesForRegion(world, region.id), [region.id, world]);

    return (
        <RegionThemeProvider region={region}>
            <RegionFogController region={region} />
            <RegionAmbience region={region} />
            <DreamSkyDome theme={region.theme} />
            <StarField reducedMotion={reducedMotion} region={region} />
            <StarTwinkleController reducedMotion={reducedMotion} />
            <NebulaCloud reducedMotion={reducedMotion} region={region} />
            <MoonOrb reducedMotion={reducedMotion} region={region} />
            <SunHalo region={region} theme={region.theme} />
            {region.weather === "aurora" ? <AuroraCurtain reducedMotion={reducedMotion} region={region} /> : null}
            {region.weather === "starfall" ? <MeteorShower reducedMotion={reducedMotion} region={region} /> : null}
            <WeatherController reducedMotion={reducedMotion} region={region} />
            <WeatherAudioBridge region={region} />

            <TerrainGenerator region={region}>
                <GroundMesh region={region} />
                <PathNetwork region={region} />
            </TerrainGenerator>
            <Bridge
                accent={region.theme.accent}
                position={[region.center[0] + 9, 0, region.center[2] + 10]}
                scale={0.8}
            />
            <Stair accent={region.theme.secondary} position={[region.center[0] - 8, 0, region.center[2] + 8]} />
            <Cliff accent={region.theme.accent} position={[region.center[0] - 16, 0, region.center[2] - 13]} />
            <HeightfieldCollider region={region} />
            <RegionBoundary region={region} />
            <RegionSpawnPoint region={region} />

            <RegionLoader label={`正在聚合${region.name}`}>
                {region.structures.map((plan, index) => (
                    <group key={plan.id}>
                        <StructureNode plan={plan} />
                        {index === 3 ? <FloatingIsland plan={plan} /> : null}
                        {index === 6 ? <ProceduralCityBlock plan={plan} /> : null}
                    </group>
                ))}
                <NatureLayer plans={region.nature} regionId={region.id} />
                <WaterLayer plans={region.water} regionId={region.id} />
                {regionMemories.slice(0, 8).map((memory) => (
                    <GroundDecal
                        accent={memory.accent}
                        key={`decal-${memory.id}`}
                        position={memory.groundPoint}
                        scale={0.7 + memory.importance * 0.9}
                    />
                ))}
            </RegionLoader>

            <WorldObjectPool name={`beacons-${region.id}`}>
                <InstancedWorldObjects count={performanceMode ? 12 : 28} region={region} />
            </WorldObjectPool>
            <StarDustParticles count={performanceMode ? 160 : 420} reducedMotion={reducedMotion} region={region} />
            <DreamFogParticles reducedMotion={reducedMotion} region={region} />

            <MemoryStarfield
                inspirationIds={inspirationIds ?? EMPTY_IDS}
                matchedIds={matchedIds}
                memories={regionMemories}
                onSelect={selectDreamMemory}
                reducedMotion={reducedMotion}
                resultOnly={resultOnly}
                selectedId={selectedMemoryId}
            />
            <ConstellationLayer memories={matchingMemories} region={region} selectedId={selectedMemoryId} />
            {regionMemories.slice(0, 2).map((memory) => (
                <MemoryPickup key={`pickup-${memory.id}`} memory={memory} />
            ))}
            {regionMemories.slice(0, 3).map((memory) => (
                <GlowAura accent={memory.accent} key={`aura-${memory.id}`} position={memory.groundPoint} scale={1.4} />
            ))}

            {region.portals.map((portal) => (
                <PortalGate key={portal.id} portal={portal} reducedMotion={reducedMotion} />
            ))}

            <PlayerMovement key={`${region.id}-${world.seed}`} region={region} />
            <PlayerAvatar />
            <PlayerShadow />
            <RunStamina />
            <FootstepController />
            <NPCQuestGiver region={region} />
            <CreatureFlock region={region} />
            <ProximityPrompt memories={regionMemories} region={region} />
        </RegionThemeProvider>
    );
}
