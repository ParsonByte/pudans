"use client";

import type { ReactNode } from "react";

import { X } from "lucide-react";

import styles from "../../styles/dream-space.module.css";

export function HudPanel({
    children,
    className,
    kicker,
    onClose,
    title,
}: {
    children: ReactNode;
    className?: string;
    kicker?: string;
    onClose?: () => void;
    title: string;
}) {
    return (
        <section className={`${styles.glassPanel} ${className ?? ""}`}>
            <header className={styles.panelHeader}>
                <div>
                    <h2 className={styles.panelTitle}>{title}</h2>
                    {kicker ? <p className={styles.panelKicker}>{kicker}</p> : null}
                </div>
                {onClose ? (
                    <button aria-label={`关闭${title}`} className={styles.panelClose} onClick={onClose} type="button">
                        <X aria-hidden size={14} />
                    </button>
                ) : null}
            </header>
            <div className={`${styles.panelBody} ${styles.panelScroller}`}>{children}</div>
        </section>
    );
}
