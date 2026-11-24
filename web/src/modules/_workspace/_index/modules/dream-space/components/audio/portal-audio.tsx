"use client";

import { useEffect, useRef } from "react";

import { useDreamStore } from "../../stores/use-dream-store";
import { playDreamCue } from "../../world/audio-runtime";

export function PortalAudio() {
    const openedPortalIds = useDreamStore((state) => state.openedPortalIds);
    const previousCount = useRef(openedPortalIds.length);

    useEffect(() => {
        if (openedPortalIds.length > previousCount.current) {
            playDreamCue("portal", 1);
        }

        previousCount.current = openedPortalIds.length;
    }, [openedPortalIds.length]);

    return null;
}
