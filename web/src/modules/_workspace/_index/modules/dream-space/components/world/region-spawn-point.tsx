"use client";

import type { RegionDefinition } from "../../types";
import { PlayerSpawn } from "../player/player-spawn";

export function RegionSpawnPoint({ region }: { region: RegionDefinition }) {
    return (
        <group>
            <PlayerSpawn region={region} />
            <pointLight
                color={region.theme.star}
                distance={8}
                intensity={1.4}
                position={[region.spawn[0], 2, region.spawn[2]]}
            />
        </group>
    );
}
