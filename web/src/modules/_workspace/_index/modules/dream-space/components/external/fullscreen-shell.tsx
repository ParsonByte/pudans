"use client";

import type { ReactNode } from "react";

import styles from "../../styles/dream-space.module.css";

export function FullscreenShell({ children }: { children: ReactNode }) {
    return <div className={`${styles.externalPage} ${styles.fullscreenShell}`}>{children}</div>;
}
