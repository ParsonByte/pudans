"use client";

import { useEffect } from "react";

import { dreamStore, persistDreamSnapshot } from "../../stores/dream-store";

export function WorldSaveSnapshot() {
    useEffect(() => {
        let timer: number | undefined;
        const unsubscribe = dreamStore.subscribe(() => {
            if (timer !== undefined) {
                return;
            }

            timer = window.setTimeout(() => {
                timer = undefined;
                persistDreamSnapshot();
            }, 450);
        });

        return () => {
            window.clearTimeout(timer);
            unsubscribe();
            persistDreamSnapshot();
        };
    }, []);

    return null;
}
