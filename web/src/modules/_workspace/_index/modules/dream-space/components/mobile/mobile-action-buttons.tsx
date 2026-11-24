"use client";

import { Hand, Map, PersonStanding, SlidersHorizontal, Sparkles, Video } from "lucide-react";

import { setDreamCameraMode, toggleDreamPanel } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { playerRuntime } from "../../world/player-runtime";

export function MobileActionButtons() {
    const cameraMode = useDreamStore((state) => state.cameraMode);

    return (
        <div className={styles.mobileActions}>
            <button
                aria-label="打开地图"
                className={styles.mobileAction}
                onClick={() => toggleDreamPanel("map")}
                type="button"
            >
                <Map aria-hidden size={18} />
            </button>
            <button
                aria-label="打开记忆筛选"
                className={styles.mobileAction}
                onClick={() => toggleDreamPanel("filters")}
                type="button"
            >
                <SlidersHorizontal aria-hidden size={18} />
            </button>
            <button
                aria-label="打开灵感重组"
                className={styles.mobileAction}
                onClick={() => toggleDreamPanel("inspiration")}
                type="button"
            >
                <Sparkles aria-hidden size={18} />
            </button>
            <button
                aria-label={cameraMode === "third" ? "切换第一人称" : "切换第三人称"}
                className={styles.mobileAction}
                onClick={() => setDreamCameraMode(cameraMode === "third" ? "first" : "third")}
                type="button"
            >
                {cameraMode === "third" ? <PersonStanding aria-hidden size={18} /> : <Video aria-hidden size={18} />}
            </button>
            <button
                aria-label="与附近对象交互"
                className={`${styles.mobileAction} ${styles.mobileActionPrimary}`}
                onClick={() => {
                    playerRuntime.interactRequested = true;
                }}
                type="button"
            >
                <Hand aria-hidden size={22} />
            </button>
        </div>
    );
}
