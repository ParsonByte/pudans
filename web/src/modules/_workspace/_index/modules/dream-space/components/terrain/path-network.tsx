"use client";

import type { RegionDefinition } from "../../types";
import { RoadSegment } from "./road-segment";

export function PathNetwork({ region }: { region: RegionDefinition }) {
    const positions = region.portals.slice(0, 6).map((portal, index) => {
        const dx = portal.position[0] - region.center[0];
        const dz = portal.position[2] - region.center[2];
        const length = Math.hypot(dx, dz);
        const rotation = -Math.atan2(dx, dz);

        return {
            key: portal.id,
            length,
            position: [dx / 2, 0, dz / 2] as [number, number, number],
            rotation: rotation + index * 0,
        };
    });

    return (
        <group>
            {positions.map((path, index) => (
                <RoadSegment
                    accent={region.theme.accent}
                    key={path.key}
                    length={path.length}
                    position={path.position}
                    rotation={path.rotation}
                    width={index === 0 ? 1.8 : 1.1}
                />
            ))}
        </group>
    );
}
