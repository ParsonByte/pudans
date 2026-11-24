"use client";

import { toggleDreamPanel } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { HudPanel } from "../hud/hud-panel";
import { PlayerDiscoveryLog } from "../player/player-discovery-log";
import { PlayerInventory } from "../player/player-inventory";
import { PlayerRespawn } from "../player/player-respawn";

export function DreamJournal() {
    const world = useDreamStore((state) => state.world);
    const inspiration = useDreamStore((state) => state.inspiration);

    return (
        <HudPanel
            className={styles.widePanel}
            kicker="DREAM JOURNAL"
            onClose={() => toggleDreamPanel("journal")}
            title="梦境日志"
        >
            <div className="flex items-center justify-between gap-3">
                <PlayerInventory />
                <span className={styles.seedBadge}>SEED {world?.shortCode ?? "PENDING"}</span>
            </div>
            <div className="mt-3">
                <PlayerDiscoveryLog />
            </div>
            <div className={`${styles.memoryRelations} mt-4`}>
                <span className={styles.filterLabel}>灵感收藏</span>
                {inspiration ? (
                    <div className={styles.subPanel}>
                        <p className={styles.narrative}>{inspiration.title}</p>
                        <p className={styles.portalHint}>{inspiration.saved ? "已收藏进本次梦境" : "尚未收藏"}</p>
                    </div>
                ) : (
                    <p className={styles.emptyState}>还没有生成灵感情节。</p>
                )}
            </div>
            <div className="mt-3">
                <PlayerRespawn />
            </div>
        </HudPanel>
    );
}
