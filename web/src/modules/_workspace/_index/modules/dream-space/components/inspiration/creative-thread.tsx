"use client";

import styles from "../../styles/dream-space.module.css";

export function CreativeThread({ keywords }: { keywords: string[] }) {
    return (
        <div className={styles.inspirationKeywords}>
            {keywords.map((keyword, index) => (
                <span className={`${styles.tag} ${styles.tagStatic}`} key={keyword}>
                    {String(index + 1).padStart(2, "0")} · {keyword}
                </span>
            ))}
        </div>
    );
}
