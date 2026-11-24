"use client";

import { toggleDreamPanel } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { regionById } from "../../world/world-generator";
import { HudPanel } from "../hud/hud-panel";
import { DialogueTrigger } from "../interaction/dialogue-trigger";
import { NPCDialogueBubble } from "../interaction/npc-dialogue-bubble";
import { EchoNarrator } from "../narrative/echo-narrator";
import { RegionStory } from "../narrative/region-story";
import { EchoDialogue } from "./echo-dialogue";
import { LoreFragment } from "./lore-fragment";
import { RiddleGate } from "./riddle-gate";
import { SessionStoryRecap } from "./session-story-recap";
import { WhisperText } from "./whisper-text";

export function DreamNarrativePanel() {
    const world = useDreamStore((state) => state.world);
    const activeRegionId = useDreamStore((state) => state.activeRegionId);
    const region = world ? regionById(world, activeRegionId) : undefined;

    if (!region) {
        return null;
    }

    return (
        <HudPanel
            className={styles.widePanel}
            kicker={region.subtitle}
            onClose={() => toggleDreamPanel("story")}
            title={region.name}
        >
            <WhisperText text={region.ambientText} />
            <div className="mt-3">
                <RegionStory region={region} />
            </div>
            <EchoNarrator line="记忆不是被保存的过去，而是仍在生长的坐标。" />
            <div className="mt-3">
                <LoreFragment region={region} />
            </div>
            <div className="mt-3">
                <SessionStoryRecap />
            </div>
            <div className="mt-3">
                <EchoDialogue />
            </div>
            <div className="mt-3">
                <NPCDialogueBubble text="回声行者把一枚未命名的光放在你面前，等你决定它是否值得被记住。" />
                <DialogueTrigger
                    className={`${styles.secondaryButton} mt-2`}
                    text="回声行者：每一条记忆都在等你再次经过。"
                />
            </div>
            <div className="mt-3">
                <RiddleGate />
            </div>
        </HudPanel>
    );
}
