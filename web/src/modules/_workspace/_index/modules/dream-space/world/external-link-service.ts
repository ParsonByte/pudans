import { portalRegistry } from "./portal-registry";

const ALLOWED_PROTOCOLS = new Set(["https:"]);

export type ExternalLinkDecision = { allowed: true; url: URL } | { allowed: false; reason: string };

export function resolveExternalLink(rawUrl: string): ExternalLinkDecision {
    try {
        const url = new URL(rawUrl);

        if (!ALLOWED_PROTOCOLS.has(url.protocol)) {
            return { allowed: false, reason: "仅允许 HTTPS 外部门户" };
        }

        if (!portalRegistry.isAllowedHost(url.hostname)) {
            return { allowed: false, reason: "目标域名不在门户白名单内" };
        }

        return { allowed: true, url };
    } catch {
        return { allowed: false, reason: "门户地址无效" };
    }
}

export function openExternalLink(rawUrl: string): ExternalLinkDecision {
    const decision = resolveExternalLink(rawUrl);

    if (decision.allowed) {
        window.open(decision.url.toString(), "_blank", "noopener,noreferrer");
    }

    return decision;
}

export function externalPortalHref(portalId: string): string {
    return `/dream-space/external/${encodeURIComponent(portalId)}`;
}
