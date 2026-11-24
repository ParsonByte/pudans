"use client";

import { useMemo } from "react";

import { Sparkles } from "lucide-react";

import { setDreamInspiration, toggleDreamInspirationSaved, toggleDreamPanel } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { createInspiration, filterMemories } from "../../world/memory-queries";
import { HudPanel } from "../hud/hud-panel";
import { CreativeThread } from "../inspiration/creative-thread";
import { InspirationOrbit } from "../inspiration/inspiration-orbit";
import { InspirationCardDeck } from "./inspiration-card-deck";

export function InspirationModePanel() {
    const world = useDreamStore((state) => state.world);
    const filters = useDreamStore((state) => state.filters);
    const selectedMemoryId = useDreamStore((state) => state.selectedMemoryId);
    const inspiration = useDreamStore((state) => state.inspiration);
    const inspirationRound = useDreamStore((state) => state.inspirationRound);
    const filtered = useMemo(() => (world ? filterMemories(world.memories, filters) : []), [filters, world]);

    const generate = () => {
        if (!world) {
            return;
        }

        setDreamInspiration(createInspiration(filtered, selectedMemoryId, Date.now() + inspirationRound));
    };

    return (
        <HudPanel
            className={styles.widePanel}
            kicker="INSPIRATION ENGINE"
            onClose={() => toggleDreamPanel("inspiration")}
            title="灵感重组"
        >
            <p className={styles.narrative}>
                从当前可见的记忆星辰中抽取一条关联链，把情绪、标签与叙事线重新组成可继续创作的星座。
            </p>
            <button className={`${styles.primaryButton} mt-3 w-full`} onClick={generate} type="button">
                <Sparkles aria-hidden size={13} />
                {inspiration ? "重新生成星座" : "生成灵感星座"}
            </button>
            {inspiration ? (
                <div className="mt-3">
                    <CreativeThread keywords={inspiration.keywords} />
                    <InspirationCardDeck card={inspiration} onRefresh={generate} onSave={toggleDreamInspirationSaved} />
                </div>
            ) : (
                <div className={styles.emptyState}>
                    <InspirationOrbit accent="#FFC857" count={filtered.length} />
                    还没有灵感星座。选择一条记忆后再生成。
                </div>
            )}
        </HudPanel>
    );
}
