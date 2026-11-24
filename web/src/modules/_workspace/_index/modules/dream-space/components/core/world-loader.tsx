"use client";

import { Orbit } from "lucide-react";

import styles from "../../styles/dream-space.module.css";

export function WorldLoader({ label }: { label: string }) {
    return (
        <div className={styles.worldLoader} role="status">
            <span className={styles.loaderOrb}>
                <Orbit aria-hidden size={28} />
            </span>
            <strong>{label}</strong>
            <small>FOLDING DREAM LAYER</small>
        </div>
    );
}
