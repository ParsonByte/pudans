"use client";

import { useLayoutEffect, useMemo, useRef, useState } from "react";

import { Html } from "@react-three/drei";
import { type ThreeEvent, useFrame } from "@react-three/fiber";
import * as THREE from "three";

import { MEMORY_TYPE_META } from "../../constants";
import { setDreamHoveredMemory } from "../../stores/dream-store";
import styles from "../../styles/dream-space.module.css";
import type { MemoryLayout } from "../../types";
import { GroundProjection } from "../fx/ground-projection";
import { InspirationSpark } from "../fx/inspiration-spark";
import { LightBeam } from "../fx/light-beam";
import { MemoryTrail } from "../fx/memory-trail";
import { RippleEffect } from "../fx/ripple-effect";
import { ShatterEffect } from "../fx/shatter-effect";
import { HoverHighlight } from "../interaction/hover-highlight";

export function MemoryStarfield({
    inspirationIds,
    matchedIds,
    memories,
    onSelect,
    reducedMotion,
    resultOnly,
    selectedId,
}: {
    inspirationIds: string[];
    matchedIds: Set<string>;
    memories: MemoryLayout[];
    onSelect: (memoryId: string) => void;
    reducedMotion: boolean;
    resultOnly: boolean;
    selectedId: string | null;
}) {
    const mesh = useRef<THREE.InstancedMesh>(null);
    const dummy = useMemo(() => new THREE.Object3D(), []);
    const scales = useRef(new Float32Array(memories.length));
    const targets = useRef(new Float32Array(memories.length));
    const initialized = useRef(false);
    const [hoveredId, setHoveredId] = useState<string | null>(null);
    const selected = memories.find((memory) => memory.id === selectedId);
    const hovered = memories.find((memory) => memory.id === hoveredId);
    const inspirationSet = useMemo(() => new Set(inspirationIds), [inspirationIds]);
    const inspirationAnchor = memories.find((memory) => inspirationIds.includes(memory.id));
    const color = useMemo(() => new THREE.Color(), []);

    useLayoutEffect(() => {
        if (!mesh.current) {
            return;
        }

        memories.forEach((memory, index) => {
            const isMatch = matchedIds.has(memory.id);
            const active = memory.id === selectedId || memory.id === hoveredId || inspirationSet.has(memory.id);
            const hidden = resultOnly && !isMatch;
            const base = 0.13 + memory.importance * 0.2;

            targets.current[index] = hidden ? 0 : base * (active ? 1.55 : isMatch ? 1 : 0.58);
            color.set(MEMORY_TYPE_META[memory.type].color).multiplyScalar(isMatch ? 1 : 0.32);
            mesh.current?.setColorAt(index, color);

            if (!initialized.current) {
                dummy.position.set(...memory.position);
                dummy.scale.setScalar(0);
                dummy.updateMatrix();
                mesh.current?.setMatrixAt(index, dummy.matrix);
            }
        });

        if (mesh.current.instanceColor) {
            mesh.current.instanceColor.needsUpdate = true;
        }

        if (!initialized.current) {
            mesh.current.instanceMatrix.needsUpdate = true;
            initialized.current = true;
        }
    }, [color, dummy, hoveredId, inspirationSet, matchedIds, memories, resultOnly, selectedId]);

    useFrame(({ clock }, delta) => {
        if (!mesh.current) {
            return;
        }

        let changed = false;

        memories.forEach((memory, index) => {
            const current = scales.current[index];
            const target = targets.current[index];
            const next = current + (target - current) * Math.min(1, delta * 8);

            if (Math.abs(current - target) > 0.0002) {
                const breath = reducedMotion
                    ? 1
                    : 1 + Math.sin(clock.elapsedTime * 0.72 + memory.monthIndex * 0.8) * 0.08;
                scales.current[index] = next;
                dummy.position.set(...memory.position);
                dummy.scale.setScalar(next * breath);
                dummy.updateMatrix();
                mesh.current?.setMatrixAt(index, dummy.matrix);
                changed = true;
            }
        });

        if (changed) {
            mesh.current.instanceMatrix.needsUpdate = true;
        }
    });

    const handleMove = (event: ThreeEvent<PointerEvent>) => {
        event.stopPropagation();

        if (event.instanceId === undefined) {
            return;
        }

        const memory = memories[event.instanceId];

        if (!memory || (resultOnly && !matchedIds.has(memory.id))) {
            return;
        }

        setHoveredId(memory.id);
        setDreamHoveredMemory(memory.id);
        document.body.style.cursor = "pointer";
    };

    const handleOut = () => {
        setHoveredId(null);
        setDreamHoveredMemory(null);
        document.body.style.cursor = "";
    };

    const handleClick = (event: ThreeEvent<MouseEvent>) => {
        event.stopPropagation();

        if (event.instanceId === undefined) {
            return;
        }

        const memory = memories[event.instanceId];

        if (memory && (!resultOnly || matchedIds.has(memory.id))) {
            onSelect(memory.id);
        }
    };

    return (
        <group>
            <instancedMesh
                args={[undefined, undefined, memories.length]}
                frustumCulled={false}
                onClick={handleClick}
                onPointerMove={handleMove}
                onPointerOut={handleOut}
                ref={mesh}
            >
                <sphereGeometry args={[1, 14, 10]} />
                <meshBasicMaterial fog={false} toneMapped={false} />
            </instancedMesh>

            {memories
                .filter((memory) => memory.importance >= 0.84)
                .map((memory) => (
                    <LightBeam
                        accent={memory.accent}
                        from={memory.position}
                        intensity={memory.id === selectedId ? 1.6 : 0.52}
                        key={`beam-${memory.id}`}
                        to={memory.groundPoint}
                    />
                ))}

            {selected ? (
                <>
                    <MemoryTrail memory={selected} />
                    <RippleEffect accent={selected.accent} position={selected.groundPoint} />
                    <GroundProjection memory={selected} />
                    <ShatterEffect
                        accent={selected.accent}
                        position={[selected.groundPoint[0], 0.12, selected.groundPoint[2]]}
                        seed={selected.timestamp}
                    />
                </>
            ) : null}

            {hovered ? <HoverHighlight memory={hovered} /> : null}
            {inspirationAnchor ? (
                <InspirationSpark
                    accent={inspirationAnchor.accent}
                    origin={[inspirationAnchor.groundPoint[0], 0, inspirationAnchor.groundPoint[2]]}
                    seed={inspirationAnchor.timestamp}
                />
            ) : null}

            {hovered ? (
                <Html center className={styles.starTooltip} distanceFactor={10} position={hovered.position}>
                    <span style={{ color: MEMORY_TYPE_META[hovered.type].color }}>
                        {MEMORY_TYPE_META[hovered.type].label}
                    </span>
                    <strong>{hovered.title}</strong>
                    <span>{hovered.tags.slice(0, 2).join(" · ")}</span>
                </Html>
            ) : null}
        </group>
    );
}
