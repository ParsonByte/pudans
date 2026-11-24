"use client";

import { Camera } from "lucide-react";

import { pushDreamToast } from "../../stores/dream-store";
import styles from "../../styles/dream-space.module.css";

export function PhotoModeCamera() {
    return (
        <button
            aria-label="复制当前画面说明"
            className={styles.iconButton}
            onClick={() => {
                void navigator.clipboard?.writeText(`${document.title} · DANS Dream Layer`);
                pushDreamToast("已复制当前梦境坐标说明", "info");
            }}
            type="button"
        >
            <Camera aria-hidden size={15} />
        </button>
    );
}
