"use client";

import { useMemo, useState } from "react";

import { useRouter } from "next/navigation";

import { ArrowUpRight, Globe2, X } from "lucide-react";

import { pushDreamToast, setDreamOpenedPortal, setDreamPreviewPortal } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import type { PortalDefinition } from "../../types";
import { externalPortalHref, openExternalLink, resolveExternalLink } from "../../world/external-link-service";
import { portalRegistry } from "../../world/portal-registry";
import { PortalTrigger } from "../interaction/portal-trigger";
import { PortalCooldown } from "./portal-cooldown";
import { PortalDestinationBadge } from "./portal-destination-badge";
import { PortalLoadingTransition } from "./portal-loading-transition";
import { PortalPermissionHint } from "./portal-permission-hint";

function findPortal(portals: PortalDefinition[], id: string | null): PortalDefinition | undefined {
    return id ? portals.find((portal) => portal.id === id) : undefined;
}

export function PortalPreview() {
    const router = useRouter();
    const previewPortalId = useDreamStore((state) => state.previewPortalId);
    const world = useDreamStore((state) => state.world);
    const portals = useMemo(() => world?.regions.flatMap((region) => region.portals) ?? [], [world]);
    const portal = findPortal(portals, previewPortalId);
    const [opening, setOpening] = useState(false);
    const [cooldownPortalId, setCooldownPortalId] = useState<string | null>(null);

    if (!portal) {
        return null;
    }

    const externalRecord = portalRegistry.get(portal.id);
    const linkDecision = portal.externalUrl ? resolveExternalLink(portal.externalUrl) : null;

    const launchExternal = () => {
        if (!portal.externalUrl) {
            return;
        }

        setOpening(true);
        const result = openExternalLink(portal.externalUrl);

        if (!result.allowed) {
            pushDreamToast(result.reason, "warning");
            return;
        }

        setDreamOpenedPortal(portal.id);
        setCooldownPortalId(portal.id);
        window.setTimeout(() => setOpening(false), 720);
    };

    const openInternal = () => {
        setOpening(true);
        setDreamPreviewPortal(null);
        setDreamOpenedPortal(portal.id);
        router.push(externalPortalHref(portal.id));
    };

    return (
        <section aria-label={`门户预览：${portal.label}`} className={styles.portalPreview}>
            <div className={styles.portalPreviewHeader}>
                <div>
                    <p className={styles.projectionKicker}>
                        {portal.kind === "external" ? "EXTERNAL PORTAL" : "REGION PORTAL"}
                    </p>
                    <h2 className={styles.portalPreviewTitle}>{portal.label}</h2>
                    <p className={styles.portalPreviewText}>{portal.description}</p>
                </div>
                <button
                    aria-label="关闭门户预览"
                    className={styles.panelClose}
                    onClick={() => setDreamPreviewPortal(null)}
                    type="button"
                >
                    <X aria-hidden size={14} />
                </button>
            </div>
            <div className={styles.panelBody}>
                <PortalDestinationBadge
                    accent={portal.accent}
                    host={externalRecord?.host}
                    label={portal.targetRegionId ? "梦境区域" : "独立浏览器"}
                />
                <PortalCooldown cooldownMs={portal.cooldownMs} opened={cooldownPortalId === portal.id} />
                <PortalPermissionHint
                    kind={portal.kind}
                    reason={linkDecision?.allowed === false ? linkDecision.reason : undefined}
                />
                {opening ? <PortalLoadingTransition /> : null}
                <div className={styles.portalActions}>
                    {portal.kind === "external" ? (
                        <>
                            <button className={styles.primaryButton} onClick={launchExternal} type="button">
                                <ArrowUpRight aria-hidden size={13} />
                                新标签打开
                            </button>
                            <button className={styles.secondaryButton} onClick={openInternal} type="button">
                                <Globe2 aria-hidden size={13} />
                                本页全屏打开
                            </button>
                        </>
                    ) : (
                        <PortalTrigger className={styles.primaryButton} label="穿过门户" portalId={portal.id} />
                    )}
                </div>
            </div>
        </section>
    );
}
