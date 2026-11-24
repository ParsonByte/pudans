"use client";

import { useEffect } from "react";

import { dreamStore, setDreamHudVisible } from "../../stores/dream-store";

export function InspectMode() {
    useEffect(() => {
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.code !== "KeyI" || event.metaKey || event.ctrlKey) {
                return;
            }

            setDreamHudVisible(!dreamStore.getState().hudVisible);
        };

        window.addEventListener("keydown", onKeyDown);

        return () => window.removeEventListener("keydown", onKeyDown);
    }, []);

    return null;
}
