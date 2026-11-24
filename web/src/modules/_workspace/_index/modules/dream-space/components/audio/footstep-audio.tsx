"use client";

import { useEffect } from "react";

import { playDreamCue } from "../../world/audio-runtime";

export function FootstepAudio() {
    useEffect(() => {
        const onFootstep = (event: Event) => {
            const detail = event instanceof CustomEvent ? (event.detail as { running?: boolean }) : undefined;
            playDreamCue("footstep", detail?.running ? 1.25 : 0.8);
        };

        window.addEventListener("dans-dream:footstep", onFootstep);

        return () => window.removeEventListener("dans-dream:footstep", onFootstep);
    }, []);

    return null;
}
