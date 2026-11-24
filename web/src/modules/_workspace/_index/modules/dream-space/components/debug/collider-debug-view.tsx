"use client";

import { useDreamStore } from "../../stores/use-dream-store";

export function ColliderDebugView() {
    const debugVisible = useDreamStore((state) => state.debugVisible);

    if (!debugVisible) {
        return null;
    }

    return (
        <group position={[0, 0.02, 0]}>
            <gridHelper args={[80, 32, "#79E6B8", "#1d5745"]} />
            <axesHelper args={[3]} />
        </group>
    );
}
