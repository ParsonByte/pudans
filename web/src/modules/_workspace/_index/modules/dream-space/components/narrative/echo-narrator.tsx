"use client";

import { memo } from "react";

import styles from "../../styles/dream-space.module.css";

export const EchoNarrator = memo(function EchoNarrator({ line }: { line: string }) {
    return (
        <p aria-live="polite" className={styles.whisper}>
            “{line}”
        </p>
    );
});
