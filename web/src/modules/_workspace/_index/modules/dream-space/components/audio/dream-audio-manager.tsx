"use client";

import { useEffect } from "react";

import { useDreamStore } from "../../stores/use-dream-store";
import { setDreamAudioEnabled as setRuntimeAudioEnabled } from "../../world/audio-runtime";
import { AmbientSoundscape } from "./ambient-soundscape";
import { FootstepAudio } from "./footstep-audio";
import { MemoryAudioCue } from "./memory-audio-cue";
import { PortalAudio } from "./portal-audio";
import { SpatialAudioEmitter } from "./spatial-audio-emitter";
import { WeatherAudio } from "./weather-audio";

export function DreamAudioManager() {
    const enabled = useDreamStore((state) => state.audioEnabled);

    useEffect(() => {
        setRuntimeAudioEnabled(enabled);
    }, [enabled]);

    return (
        <>
            <AmbientSoundscape />
            <FootstepAudio />
            <MemoryAudioCue />
            <PortalAudio />
            <WeatherAudio />
            <SpatialAudioEmitter />
        </>
    );
}
