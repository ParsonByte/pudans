"use client";

import { useEffect } from "react";

import {
    setDreamDirectoryOpen,
    setDreamHudVisible,
    setDreamPaused,
    setDreamPreviewPortal,
    toggleDreamPanel,
} from "../../stores/dream-store";
import { dreamStore } from "../../stores/dream-store";
import { toggleDocumentFullscreen } from "../../world/fullscreen";

function isTypingTarget(target: EventTarget | null): boolean {
    return (
        target instanceof HTMLElement &&
        (target.tagName === "INPUT" ||
            target.tagName === "TEXTAREA" ||
            target.tagName === "SELECT" ||
            target.isContentEditable)
    );
}

export function WorldStateMachine() {
    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            if (isTypingTarget(event.target)) {
                return;
            }

            const state = dreamStore.getState();

            if (event.key === "Escape") {
                if (state.previewPortalId) {
                    setDreamPreviewPortal(null);
                    return;
                }

                if (state.directoryOpen) {
                    setDreamDirectoryOpen(false);
                    return;
                }

                if (state.panel) {
                    toggleDreamPanel(state.panel);
                    return;
                }

                setDreamPaused(!state.paused);
                return;
            }

            if (event.key === "m" || event.key === "M") {
                event.preventDefault();
                setDreamDirectoryOpen(false);
                toggleDreamPanel("map");
                return;
            }

            if (event.key === "Tab") {
                event.preventDefault();
                toggleDreamPanel("filters");
                return;
            }

            if (event.key === "f" || event.key === "F") {
                event.preventDefault();
                toggleDocumentFullscreen();
                return;
            }

            if (event.key === "h" || event.key === "H") {
                setDreamHudVisible(!state.hudVisible);
            }
        };

        window.addEventListener("keydown", handleKeyDown);

        return () => window.removeEventListener("keydown", handleKeyDown);
    }, []);

    return null;
}
