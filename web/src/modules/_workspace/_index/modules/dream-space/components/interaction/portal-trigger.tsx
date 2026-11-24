"use client";

import { useDreamStore } from "../../stores/use-dream-store";
import { activatePortal } from "../../world/interactions";

export function PortalTrigger({
    className,
    label = "激活门户",
    portalId,
}: {
    className?: string;
    label?: string;
    portalId: string;
}) {
    const portal = useDreamStore((state) =>
        state.world?.regions.flatMap((region) => region.portals).find((candidate) => candidate.id === portalId),
    );

    if (!portal) {
        return null;
    }

    return (
        <button className={className} onClick={() => activatePortal(portal)} type="button">
            {label}
        </button>
    );
}
