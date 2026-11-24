"use client";

import type { RegionDefinition } from "../../types";
import { NPCWanderer } from "./npc-wanderer";
import { QuestMarker } from "./quest-marker";

export function NPCQuestGiver({ region }: { region: RegionDefinition }) {
    return (
        <group>
            <NPCWanderer region={region} />
            <QuestMarker accent={region.theme.accent} position={[region.spawn[0] - 3, 0, region.spawn[2] - 3]} />
        </group>
    );
}
