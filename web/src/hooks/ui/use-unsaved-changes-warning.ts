"use client";

import { useEventListener } from "@/hooks/utils/use-event-listener";

export function useUnsavedChangesWarning(enabled: boolean) {
    useEventListener(
        "beforeunload",
        (event) => {
            event.preventDefault();
            event.returnValue = "";
        },
        enabled ? undefined : null,
    );
}
