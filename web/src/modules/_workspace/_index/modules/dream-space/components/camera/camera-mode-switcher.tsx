"use client";

import { Eye, PersonStanding } from "lucide-react";

import { setDreamCameraMode } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { playerRuntime } from "../../world/player-runtime";

export function CameraModeSwitcher() {
    const cameraMode = useDreamStore((state) => state.cameraMode);

    const change = () => {
        const next = cameraMode === "third" ? "first" : "third";
        playerRuntime.cameraMode = next;
        setDreamCameraMode(next);
    };

    return (
        <button
            aria-label={cameraMode === "third" ? "切换第一人称" : "切换第三人称"}
            className={styles.iconButton}
            onClick={change}
            type="button"
        >
            {cameraMode === "third" ? <Eye aria-hidden size={15} /> : <PersonStanding aria-hidden size={15} />}
        </button>
    );
}
