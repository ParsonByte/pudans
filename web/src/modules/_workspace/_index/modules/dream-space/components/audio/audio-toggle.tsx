"use client";

import { Headphones, HeadphonesIcon } from "lucide-react";

import { setDreamAudioEnabled } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";

export function AudioToggle() {
    const enabled = useDreamStore((state) => state.audioEnabled);

    return (
        <button
            aria-label={enabled ? "关闭梦境声音" : "开启梦境声音"}
            className={`${styles.iconButton} ${enabled ? styles.iconButtonActive : ""}`}
            onClick={() => setDreamAudioEnabled(!enabled)}
            type="button"
        >
            {enabled ? <Headphones aria-hidden size={15} /> : <HeadphonesIcon aria-hidden size={15} />}
        </button>
    );
}
