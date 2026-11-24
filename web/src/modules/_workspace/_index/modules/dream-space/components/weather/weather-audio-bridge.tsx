"use client";

import { useEffect } from "react";

import type { RegionDefinition } from "../../types";

export function WeatherAudioBridge({ region }: { region: RegionDefinition }) {
    useEffect(() => {
        document.documentElement.dataset.dreamWeather = region.weather;
    }, [region.weather]);

    return null;
}
