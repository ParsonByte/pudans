"use client";

import { RotateCcw } from "lucide-react";

import { resetDreamWorldPosition } from "../../stores/dream-store";

export function PlayerRespawn() {
    return (
        <button onClick={resetDreamWorldPosition} type="button">
            <RotateCcw aria-hidden size={14} />
            返回出生点
        </button>
    );
}
