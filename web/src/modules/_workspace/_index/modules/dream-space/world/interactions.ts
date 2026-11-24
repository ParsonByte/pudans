import {
    beginDreamTransition,
    enterDreamRegion,
    pushDreamToast,
    selectDreamMemory,
    setDreamOpenedPortal,
    setDreamPhase,
    setDreamPreviewPortal,
} from "../stores/dream-store";
import type { InteractionTarget, PortalDefinition } from "../types";
import { openExternalLink } from "./external-link-service";

export function activatePortal(portal: PortalDefinition): void {
    setDreamOpenedPortal(portal.id);
    setDreamPreviewPortal(null);

    if (portal.kind === "external" && portal.externalUrl) {
        const result = openExternalLink(portal.externalUrl);
        pushDreamToast(
            result.allowed ? `已打开「${portal.label}」独立页面` : result.reason,
            result.allowed ? "success" : "warning",
        );
        return;
    }

    if (portal.kind === "region" && portal.targetRegionId) {
        beginDreamTransition(`正在穿越至 ${portal.label}`);
        window.setTimeout(() => enterDreamRegion(portal.targetRegionId!, "transition"), 620);
        window.setTimeout(() => {
            setDreamPhase("ready");
            pushDreamToast(`已抵达 ${portal.label}`, "success");
        }, 1280);
    }
}

export function activateInteractionTarget(target: InteractionTarget | null): void {
    if (!target) {
        pushDreamToast("附近没有可交互的梦境对象", "info");
        return;
    }

    if (target.kind === "memory") {
        selectDreamMemory(target.id);
        return;
    }

    if (target.kind === "portal") {
        setDreamPreviewPortal(target.id);
        return;
    }

    if (target.kind === "relic" || target.kind === "npc") {
        pushDreamToast(target.subtitle, "info");
    }
}
