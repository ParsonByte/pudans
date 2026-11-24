"use client";

import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { PerformanceDebugHUD } from "./performance-debug-hud";
import { PortalDebugList } from "./portal-debug-list";
import { RegionDebugOverlay } from "./region-debug-overlay";
import { SeedDebugPanel } from "./seed-debug-panel";

export function DebugWorldInspector() {
    const debugVisible = useDreamStore((state) => state.debugVisible);

    if (!debugVisible) {
        return null;
    }

    return (
        <aside aria-label="幻境调试面板" className={styles.debugPanel}>
            <p className={styles.panelKicker}>WORLD INSPECTOR</p>
            <SeedDebugPanel />
            <RegionDebugOverlay />
            <PortalDebugList />
            <PerformanceDebugHUD />
        </aside>
    );
}
