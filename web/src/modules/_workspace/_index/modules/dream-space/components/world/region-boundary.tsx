"use client";

import { CuboidCollider, RigidBody } from "@react-three/rapier";
import * as THREE from "three";

import type { RegionDefinition } from "../../types";

const SEGMENTS = 20;
const WALL_HEIGHT = 3;
const WALL_THICKNESS = 0.5;

export function RegionBoundary({ region }: { region: RegionDefinition }) {
    const wallLength = (Math.PI * 2 * (region.radius + 0.4)) / SEGMENTS;

    return (
        <group>
            <RigidBody colliders={false} position={[region.center[0], 0, region.center[2]]} type="fixed">
                {Array.from({ length: SEGMENTS }, (_, index) => {
                    const angle = (index / SEGMENTS) * Math.PI * 2;
                    const radius = region.radius + 0.4;

                    return (
                        <CuboidCollider
                            args={[WALL_THICKNESS, WALL_HEIGHT, wallLength / 2]}
                            key={index}
                            position={[Math.cos(angle) * radius, WALL_HEIGHT, Math.sin(angle) * radius]}
                            rotation={[0, -angle, 0]}
                        />
                    );
                })}
            </RigidBody>
            <mesh
                position={[region.center[0], 0.035, region.center[2]]}
                rotation={[-Math.PI / 2, 0, 0]}
                renderOrder={1}
            >
                <ringGeometry args={[region.radius - 0.2, region.radius + 0.12, 128]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={region.theme.accent}
                    depthWrite={false}
                    opacity={0.16}
                    side={THREE.DoubleSide}
                    transparent
                />
            </mesh>
        </group>
    );
}
