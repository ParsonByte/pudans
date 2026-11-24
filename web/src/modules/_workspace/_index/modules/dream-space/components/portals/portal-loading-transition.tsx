"use client";

import styles from "../../styles/dream-space.module.css";

export function PortalLoadingTransition() {
    return (
        <div aria-live="polite" className={styles.portalLoading}>
            <span aria-hidden />
            正在校准门户出口
        </div>
    );
}
