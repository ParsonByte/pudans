"use client";

import { useEffect, useState } from "react";

import styles from "../../styles/dream-space.module.css";

export function PortalCooldown({ cooldownMs, opened }: { cooldownMs: number; opened: boolean }) {
    const [remaining, setRemaining] = useState(0);

    useEffect(() => {
        if (!opened || cooldownMs <= 0) {
            return;
        }

        const resetTimer = window.setTimeout(() => setRemaining(cooldownMs), 0);
        const startedAt = performance.now();
        const timer = window.setInterval(() => {
            const next = Math.max(0, cooldownMs - (performance.now() - startedAt));
            setRemaining(next);

            if (next === 0) {
                window.clearInterval(timer);
            }
        }, 80);

        return () => {
            window.clearTimeout(resetTimer);
            window.clearInterval(timer);
        };
    }, [cooldownMs, opened]);

    if (remaining <= 0) {
        return null;
    }

    return <p className={styles.portalCooldown}>梦境稳定中 · {(remaining / 1000).toFixed(1)}s</p>;
}
