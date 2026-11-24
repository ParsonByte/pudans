"use client";

import { Compass as CompassIcon, PanelRightOpen } from "lucide-react";

import { GAME_KEY_HINTS } from "../../constants";
import { setDreamHudVisible } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { DebugWorldInspector } from "../debug/debug-world-inspector";
import { DreamPanelRouter } from "../panels/dream-panel-router";
import { MemoryDirectoryPanel } from "../panels/memory-directory-panel";
import { PortalPreview } from "../portals/portal-preview";
import { Compass } from "./compass";
import { InteractionHint } from "./interaction-hint";
import { LegendPanel } from "./legend-panel";
import { MiniMap } from "./mini-map";
import { PauseMenu } from "./pause-menu";
import { PlayerVitals } from "./player-vitals";
import { TimeSlider } from "./time-slider";
import { ToastCenter } from "./toast-center";
import { TopBar } from "./top-bar";

export function DreamHUD() {
    const hudVisible = useDreamStore((state) => state.hudVisible);
    const directoryOpen = useDreamStore((state) => state.directoryOpen);
    const reducedMotion = useDreamStore((state) => state.reducedMotion);

    if (!hudVisible) {
        return (
            <div className={styles.hud}>
                <div className={styles.topActions}>
                    <button
                        aria-label="恢复梦境界面"
                        className={styles.iconButton}
                        onClick={() => setDreamHudVisible(true)}
                        type="button"
                    >
                        <PanelRightOpen aria-hidden size={15} />
                    </button>
                </div>
                <ToastCenter />
            </div>
        );
    }

    return (
        <div className={`${styles.hud} ${reducedMotion ? styles.reducedMotion : ""}`}>
            <TopBar />
            {!directoryOpen ? (
                <div className={styles.hudLeft}>
                    <MiniMap />
                    <LegendPanel />
                </div>
            ) : (
                <MemoryDirectoryPanel />
            )}
            <div className="absolute top-[88px] left-1/2 -translate-x-1/2">
                <InteractionHint />
            </div>
            {!directoryOpen ? (
                <div className={styles.hudRight}>
                    <DreamPanelRouter />
                </div>
            ) : null}
            <div className={styles.hudBottom}>
                <TimeSlider />
                <div className="flex items-end gap-2">
                    <div className="hidden md:block">
                        <Compass />
                    </div>
                    <PlayerVitals />
                </div>
            </div>
            <div className="absolute bottom-24 left-[18px] hidden items-center gap-2 text-[8px] tracking-[0.12em] text-white/28 md:flex">
                <CompassIcon aria-hidden size={10} />
                {GAME_KEY_HINTS.map((hint) => `${hint.keys} ${hint.label}`).join(" · ")}
            </div>
            <PortalPreview />
            <ToastCenter />
            <PauseMenu />
            <DebugWorldInspector />
        </div>
    );
}
