"use client";

import { useEffect, useState } from "react";

import { Eye, SquareArrowOutUpRight } from "lucide-react";

import styles from "../../styles/dream-space.module.css";
import { ExternalLoadingSkeleton } from "./external-loading-skeleton";
import { NewTabLauncher } from "./new-tab-launcher";

export function IframeSandboxViewer({ label, url }: { label: string; url: string }) {
    const [open, setOpen] = useState(false);
    const [loaded, setLoaded] = useState(false);
    const [timedOut, setTimedOut] = useState(false);

    useEffect(() => {
        if (!open || loaded) {
            return;
        }

        const timer = window.setTimeout(() => setTimedOut(true), 9000);

        return () => window.clearTimeout(timer);
    }, [loaded, open]);

    if (!open) {
        return (
            <button className={styles.secondaryButton} onClick={() => setOpen(true)} type="button">
                <Eye aria-hidden size={13} />
                尝试沙箱预览
            </button>
        );
    }

    if (timedOut) {
        return (
            <div className={styles.subPanel}>
                <p className={styles.externalBadge}>IFRAME BLOCKED</p>
                <p className={styles.portalPreviewText}>目标站点拒绝沙箱内嵌，或加载超时。请改用独立浏览器页面。</p>
                <div className={styles.externalActions}>
                    <NewTabLauncher label={label} url={url} />
                    <button className={styles.ghostButton} onClick={() => setOpen(false)} type="button">
                        收起预览
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div>
            {!loaded ? <ExternalLoadingSkeleton label="沙箱正在建立隔离通道" /> : null}
            <iframe
                className={styles.externalFrame}
                onLoad={() => setLoaded(true)}
                referrerPolicy="no-referrer"
                sandbox="allow-forms allow-popups allow-scripts"
                src={url}
                title={`${label} 沙箱预览`}
            />
            <div className={styles.externalActions}>
                <span className={styles.portalHint}>
                    <SquareArrowOutUpRight aria-hidden size={11} />
                    沙箱预览仅用于辅助阅读，数据与登录态不会共享。
                </span>
                <NewTabLauncher label={label} url={url} />
            </div>
        </div>
    );
}
