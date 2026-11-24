"use client";

import { toggleDreamPanel } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { HudPanel } from "../hud/hud-panel";
import { MiniMap } from "../hud/mini-map";
import { SettingsPanel } from "../hud/settings-panel";
import { PortalRegistryView } from "../portals/portal-registry-view";
import { DreamJournal } from "./dream-journal";
import { DreamNarrativePanel } from "./dream-narrative-panel";
import { InspirationModePanel } from "./inspiration-mode-panel";
import { MemoryDetailPanel } from "./memory-detail-panel";
import { MemoryFilterPanel } from "./memory-filter-panel";

export function DreamPanelRouter() {
    const panel = useDreamStore((state) => state.panel);

    if (panel === "details") {
        return <MemoryDetailPanel />;
    }

    if (panel === "filters") {
        return <MemoryFilterPanel />;
    }

    if (panel === "inspiration") {
        return <InspirationModePanel />;
    }

    if (panel === "journal") {
        return <DreamJournal />;
    }

    if (panel === "story") {
        return <DreamNarrativePanel />;
    }

    if (panel === "settings") {
        return <SettingsPanel />;
    }

    if (panel === "map") {
        return (
            <HudPanel
                className={styles.widePanel}
                kicker="WORLD MAP"
                onClose={() => toggleDreamPanel("map")}
                title="世界地图"
            >
                <MiniMap />
                <div className="mt-3">
                    <PortalRegistryView />
                </div>
            </HudPanel>
        );
    }

    return null;
}
