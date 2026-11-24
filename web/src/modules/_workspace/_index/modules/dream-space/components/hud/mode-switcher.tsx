"use client";

import { DREAM_MODES } from "../../constants";
import { setDreamMode } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";

export function ModeSwitcher() {
    const mode = useDreamStore((state) => state.mode);

    return (
        <div aria-label="梦境模式" className={styles.modeSwitch} role="group">
            {DREAM_MODES.map((option) => (
                <button
                    aria-pressed={mode === option.id}
                    className={`${styles.modeOption} ${mode === option.id ? styles.modeOptionActive : ""}`}
                    key={option.id}
                    onClick={() => setDreamMode(option.id)}
                    title={option.description}
                    type="button"
                >
                    {option.label}
                </button>
            ))}
        </div>
    );
}
