"use client";

import { ExternalLink, ShieldCheck } from "lucide-react";

import styles from "../../styles/dream-space.module.css";
import { externalPortalHref } from "../../world/external-link-service";
import { EXTERNAL_PORTALS_LIST } from "../../world/portal-registry";

export function PortalRegistryView() {
    return (
        <div className={styles.portalRegistry}>
            {EXTERNAL_PORTALS_LIST.map((portal) => (
                <div className={styles.portalRegistryItem} key={portal.id}>
                    <span className={styles.legendSwatch} style={{ background: portal.accent, color: portal.accent }} />
                    <div className="min-w-0 flex-1">
                        <strong>{portal.label}</strong>
                        <small>{portal.host}</small>
                    </div>
                    <a aria-label={`打开${portal.label}`} href={externalPortalHref(portal.id)}>
                        <ExternalLink aria-hidden size={12} />
                    </a>
                </div>
            ))}
            <p className={styles.portalHint}>
                <ShieldCheck aria-hidden size={11} />
                所有门户地址来自静态白名单，外部页面不会进入主 Canvas。
            </p>
        </div>
    );
}
