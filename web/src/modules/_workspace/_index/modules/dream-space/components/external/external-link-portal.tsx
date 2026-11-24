"use client";

import { Globe2, ShieldCheck } from "lucide-react";

import styles from "../../styles/dream-space.module.css";
import type { ExternalPortalRecord } from "../../world/portal-registry";
import { IframeSandboxViewer } from "./iframe-sandbox-viewer";
import { NewTabLauncher } from "./new-tab-launcher";

export function ExternalLinkPortal({ portal }: { portal: ExternalPortalRecord }) {
    return (
        <article className={styles.externalCard}>
            <div className={styles.externalHero}>
                <span className={styles.externalBadge}>
                    <Globe2 aria-hidden size={11} />
                    {portal.host}
                </span>
                <h1>{portal.label}</h1>
                <p>{portal.description} 它作为独立梦境出口存在，不会把第三方页面塞进 3D 画布。</p>
                <div className={styles.externalActions}>
                    <NewTabLauncher label={portal.label} url={portal.url} />
                    <IframeSandboxViewer label={portal.label} url={portal.url} />
                </div>
                <p className={styles.portalHint}>
                    <ShieldCheck aria-hidden size={11} />
                    外链通过 HTTPS 与域名白名单双重校验；新标签页使用 noopener 与 noreferrer。
                </p>
            </div>
        </article>
    );
}
