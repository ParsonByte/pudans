"use client";

import { useEffect, useState } from "react";

import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";

export function WhisperText({ text }: { text: string }) {
    const reducedMotion = useDreamStore((state) => state.reducedMotion);
    const [visible, setVisible] = useState(reducedMotion ? text : "");

    useEffect(() => {
        if (reducedMotion) {
            const reducedTimer = window.setTimeout(() => setVisible(text), 0);

            return () => window.clearTimeout(reducedTimer);
        }

        let cursor = 0;
        const resetTimer = window.setTimeout(() => setVisible(""), 0);
        const timer = window.setInterval(() => {
            cursor += 1;
            setVisible(text.slice(0, cursor));

            if (cursor >= text.length) {
                window.clearInterval(timer);
            }
        }, 26);

        return () => {
            window.clearTimeout(resetTimer);
            window.clearInterval(timer);
        };
    }, [reducedMotion, text]);

    return (
        <p aria-live="polite" className={styles.whisper}>
            {visible}
            {visible.length < text.length ? <span className="opacity-45">▍</span> : null}
        </p>
    );
}
