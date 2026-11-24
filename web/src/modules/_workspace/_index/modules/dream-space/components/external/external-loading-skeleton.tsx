"use client";

import styles from "../../styles/dream-space.module.css";

export function ExternalLoadingSkeleton({ label = "正在准备门户容器" }: { label?: string }) {
    return (
        <div aria-live="polite" className={styles.externalSkeleton}>
            {label}
        </div>
    );
}
