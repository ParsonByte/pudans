"use client";

import { Globe2, Orbit } from "lucide-react";

import styles from "../../styles/dream-space.module.css";

export function PortalDestinationBadge({ accent, host, label }: { accent: string; host?: string; label: string }) {
    return (
        <span className={styles.portalDomain} style={{ color: accent }}>
            {host ? <Globe2 aria-hidden size={11} /> : <Orbit aria-hidden size={11} />}
            {host ?? label}
        </span>
    );
}
