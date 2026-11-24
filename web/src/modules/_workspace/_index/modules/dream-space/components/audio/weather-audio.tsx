"use client";

import { useEffect, useRef } from "react";

import { useDreamStore } from "../../stores/use-dream-store";
import { playDreamCue } from "../../world/audio-runtime";

export function WeatherAudio() {
    const weather = useDreamStore(
        (state) => state.world?.regions.find((region) => region.id === state.activeRegionId)?.weather,
    );
    const previous = useRef(weather);

    useEffect(() => {
        if (weather && previous.current && weather !== previous.current) {
            playDreamCue("weather", 0.8);
        }

        previous.current = weather;
    }, [weather]);

    return null;
}
