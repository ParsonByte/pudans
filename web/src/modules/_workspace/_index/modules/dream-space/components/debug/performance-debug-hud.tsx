"use client";

import { useEffect, useState } from "react";

import styles from "../../styles/dream-space.module.css";

export function PerformanceDebugHUD() {
    const [fps, setFps] = useState(0);

    useEffect(() => {
        let frames = 0;
        let startedAt = performance.now();
        let raf = 0;

        const measure = (now: number) => {
            frames += 1;

            if (now - startedAt >= 1000) {
                setFps(Math.round((frames * 1000) / (now - startedAt)));
                frames = 0;
                startedAt = now;
            }

            raf = window.requestAnimationFrame(measure);
        };

        raf = window.requestAnimationFrame(measure);

        return () => window.cancelAnimationFrame(raf);
    }, []);

    return (
        <div className={styles.debugItem}>
            <span>fps</span>
            <span>{fps}</span>
        </div>
    );
}
