"use client";

import { pushDreamToast } from "../../stores/dream-store";

export function DialogueTrigger({
    className,
    label = "倾听回声",
    text,
}: {
    className?: string;
    label?: string;
    text: string;
}) {
    return (
        <button className={className} onClick={() => pushDreamToast(text, "info")} type="button">
            {label}
        </button>
    );
}
