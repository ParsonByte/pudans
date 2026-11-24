"use client";

import { useLayoutEffect, useMemo, useRef } from "react";

import * as THREE from "three";

import type { RegionDefinition } from "../../types";
import { createSeededRandom } from "../../world/random";

export function InstancedWorldObjects({ count, region }: { count: number; region: RegionDefinition }) {
    const mesh = useRef<THREE.InstancedMesh>(null);
    const objects = useMemo(() => {
        const random = createSeededRandom(region.terrain.seed ^ 0x1a57);

        return Array.from({ length: count }, (_, index) => {
            const angle = random() * Math.PI * 2;
            const radius = 7 + Math.sqrt(random()) * (region.radius - 11);

            return {
                height: 0.5 + random() * 1.7,
                position: [
                    region.center[0] + Math.cos(angle) * radius,
                    0.8,
                    region.center[2] + Math.sin(angle) * radius,
                ] as [number, number, number],
                rotation: random() * Math.PI * 2,
                scale: 0.7 + random() * 0.7,
                tint: index % 3,
            };
        });
    }, [count, region]);

    useLayoutEffect(() => {
        if (!mesh.current) {
            return;
        }

        const dummy = new THREE.Object3D();
        const color = new THREE.Color();
        const palette = [region.theme.accent, region.theme.secondary, region.theme.star];

        objects.forEach((object, index) => {
            dummy.position.set(...object.position);
            dummy.rotation.set(0, object.rotation, 0);
            dummy.scale.set(object.scale, object.height, object.scale);
            dummy.updateMatrix();
            mesh.current?.setMatrixAt(index, dummy.matrix);
            mesh.current?.setColorAt(index, color.set(palette[object.tint]));
        });

        mesh.current.instanceMatrix.needsUpdate = true;

        if (mesh.current.instanceColor) {
            mesh.current.instanceColor.needsUpdate = true;
        }
    }, [objects, region.theme.accent, region.theme.secondary, region.theme.star]);

    return (
        <instancedMesh args={[undefined, undefined, count]} ref={mesh}>
            <octahedronGeometry args={[0.13, 0]} />
            <meshBasicMaterial toneMapped={false} transparent opacity={0.4} />
        </instancedMesh>
    );
}
