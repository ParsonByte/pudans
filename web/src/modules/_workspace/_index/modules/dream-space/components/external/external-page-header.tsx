"use client";

import styles from "../../styles/dream-space.module.css";
import { PortalBackButton } from "../portals/portal-back-button";
import { FullscreenToggle } from "./fullscreen-toggle";

export function ExternalPageHeader({ label, host }: { label: string; host: string }) {
    return (
        <header className={styles.externalHeader}>
            <div className={styles.externalHeaderBrand}>
                <PortalBackButton className={styles.externalBack} label="返回幻境" />
                <span className={styles.logoMark} aria-hidden />
                <div className="min-w-0">
                    <strong>{label}</strong>
                    <small>{host}</small>
                </div>
            </div>
            <FullscreenToggle />
        </header>
    );
}
