"use client";

import type { RegionDefinition } from "../../types";
import { SkyColorGradient } from "../sky/sky-color-gradient";

export function RegionFogController({ region }: { region: RegionDefinition }) {
    return <SkyColorGradient density={region.id === "portal-harbor" ? 0.019 : 0.012} theme={region.theme} />;
}
