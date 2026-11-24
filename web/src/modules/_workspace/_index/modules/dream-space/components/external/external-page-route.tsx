"use client";

import { ExternalLink } from "lucide-react";

import styles from "../../styles/dream-space.module.css";
import { portalRegistry } from "../../world/portal-registry";
import { ExternalErrorBoundary } from "./external-error-boundary";
import { ExternalLinkPortal } from "./external-link-portal";
import { ExternalPageHeader } from "./external-page-header";
import { ExternalSessionGuard } from "./external-session-guard";
import { FullscreenShell } from "./fullscreen-shell";
import { LinkWhitelistGuard } from "./link-whitelist-guard";
import { ReturnToDreamButton } from "./return-to-dream-button";

export function ExternalPageRoute({ portalId }: { portalId: string }) {
    const portal = portalRegistry.get(portalId);

    if (!portal) {
        return (
            <div className={styles.errorPanel}>
                <ExternalLink aria-hidden size={30} />
                <h1>未注册的门户</h1>
                <p>这个出口不在门户注册表内，系统不会跟随未知地址离开幻境。</p>
                <ReturnToDreamButton />
            </div>
        );
    }

    return (
        <ExternalErrorBoundary>
            <ExternalSessionGuard>
                <LinkWhitelistGuard url={portal.url}>
                    <FullscreenShell>
                        <ExternalPageHeader host={portal.host} label={portal.label} />
                        <main className={styles.externalBody}>
                            <ExternalLinkPortal portal={portal} />
                        </main>
                    </FullscreenShell>
                </LinkWhitelistGuard>
            </ExternalSessionGuard>
        </ExternalErrorBoundary>
    );
}
