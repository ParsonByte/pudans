"use client";

import { useEffect, useRef } from "react";

import { useDreamStore } from "../../stores/use-dream-store";
import { playDreamCue } from "../../world/audio-runtime";

export function MemoryAudioCue() {
    const selectedMemoryId = useDreamStore((state) => state.selectedMemoryId);
    const previous = useRef<string | null>(null);

    useEffect(() => {
        if (selectedMemoryId && selectedMemoryId !== previous.current) {
            playDreamCue("memory", 0.72);
        }

        previous.current = selectedMemoryId;
    }, [selectedMemoryId]);

    return null;
}
