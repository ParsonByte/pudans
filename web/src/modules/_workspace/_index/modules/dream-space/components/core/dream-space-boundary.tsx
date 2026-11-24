"use client";

import { useEffect } from "react";

import { pushDreamToast } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";

export function DreamSpaceBoundary() {
    const world = useDreamStore((state) => state.world);

    useEffect(() => {
        if (!world) {
            return;
        }

        const onError = (event: ErrorEvent) => {
            pushDreamToast("幻境层出现异常，可继续使用记忆列表与门户列表。", "warning");
            delete document.documentElement.dataset.dreamCinematic;
            console.error("[DANS Dream]", event.error);
        };

        window.addEventListener("error", onError);

        return () => window.removeEventListener("error", onError);
    }, [world]);

    return null;
}
