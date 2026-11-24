"use client";

import { setDreamPanel } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { DreamPanelRouter } from "../panels/dream-panel-router";

export function MobileDrawer() {
    const panel = useDreamStore((state) => state.panel);

    if (!panel) {
        return null;
    }

    return (
        <div className={styles.mobileOnly}>
            <div className={styles.drawerBackdrop} onClick={() => setDreamPanel(null)} role="presentation" />
            <section aria-label="梦境抽屉" className={styles.drawer}>
                <div className={styles.drawerHandle} />
                <div className="max-h-[72dvh] overflow-auto p-3">
                    <DreamPanelRouter />
                </div>
            </section>
        </div>
    );
}
