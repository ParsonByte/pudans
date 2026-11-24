"use client";

import type { RegionDefinition } from "../../types";
import { CloudLayer } from "./cloud-layer";
import { FogVolume } from "./fog-volume";
import { LightningFlash } from "./lightning-flash";
import { RainSystem } from "./rain-system";
import { SnowSystem } from "./snow-system";
import { WindField } from "./wind-field";

export function WeatherController({ region, reducedMotion }: { region: RegionDefinition; reducedMotion: boolean }) {
    if (region.weather === "starfall") {
        return (
            <>
                <SnowSystem region={region} reducedMotion={reducedMotion} />
                <WindField region={region} reducedMotion={reducedMotion} />
            </>
        );
    }

    if (region.weather === "petals") {
        return (
            <>
                <SnowSystem region={region} reducedMotion={reducedMotion} />
                <CloudLayer region={region} reducedMotion={reducedMotion} />
            </>
        );
    }

    if (region.weather === "embers") {
        return (
            <>
                <RainSystem region={region} reducedMotion={reducedMotion} />
                <LightningFlash region={region} reducedMotion={reducedMotion} />
            </>
        );
    }

    if (region.weather === "mist") {
        return (
            <>
                <FogVolume region={region} reducedMotion={reducedMotion} />
                <CloudLayer region={region} reducedMotion={reducedMotion} />
            </>
        );
    }

    if (region.weather === "aurora") {
        return <WindField region={region} reducedMotion={reducedMotion} />;
    }

    return <CloudLayer region={region} reducedMotion={reducedMotion} />;
}
