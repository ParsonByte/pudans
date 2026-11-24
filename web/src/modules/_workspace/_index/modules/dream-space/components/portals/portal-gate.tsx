"use client";

import { Html } from "@react-three/drei";
import * as THREE from "three";

import { setDreamPreviewPortal } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import type { PortalDefinition } from "../../types";
import { PortalVortex } from "../fx/portal-vortex";

export function PortalGate({ portal, reducedMotion }: { portal: PortalDefinition; reducedMotion: boolean }) {
    const previewPortalId = useDreamStore((state) => state.previewPortalId);
    const active = previewPortalId === portal.id;

    return (
        <group
            onClick={(event) => {
                event.stopPropagation();
                setDreamPreviewPortal(portal.id);
            }}
            position={[portal.position[0], portal.position[1] + 2.3, portal.position[2]]}
        >
            <mesh>
                <torusGeometry args={[1.65, 0.07, 8, 72]} />
                <meshBasicMaterial color={portal.accent} toneMapped={false} transparent opacity={active ? 0.9 : 0.52} />
            </mesh>
            <mesh scale={1.35}>
                <torusGeometry args={[1.65, 0.02, 6, 72]} />
                <meshBasicMaterial color={portal.accent} toneMapped={false} transparent opacity={0.24} />
            </mesh>
            <PortalVortex accent={portal.accent} reducedMotion={reducedMotion} />
            <pointLight color={portal.accent} distance={12} intensity={active ? 4.2 : 1.4} />
            {active ? (
                <Html center distanceFactor={13} position={[0, 2.3, 0]}>
                    <span className={styles.npcBubble}>{portal.label}</span>
                </Html>
            ) : null}
            <mesh position={[0, -2.2, 0]} rotation={[-Math.PI / 2, 0, 0]}>
                <ringGeometry args={[0.72, 1.42, 64]} />
                <meshBasicMaterial
                    blending={THREE.AdditiveBlending}
                    color={portal.accent}
                    depthWrite={false}
                    opacity={0.1}
                    transparent
                />
            </mesh>
        </group>
    );
}
