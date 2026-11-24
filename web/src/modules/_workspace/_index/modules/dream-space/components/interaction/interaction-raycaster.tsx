"use client";

import { useThree } from "@react-three/fiber";
import * as THREE from "three";

import { useDreamStore } from "../../stores/use-dream-store";

export function InteractionRaycaster() {
    const camera = useThree((state) => state.camera);
    const selected = useDreamStore((state) =>
        state.selectedMemoryId
            ? state.world?.memories.find((memory) => memory.id === state.selectedMemoryId)
            : undefined,
    );

    if (!selected) {
        return null;
    }

    const target = new THREE.Vector3(...selected.position);
    const distance = camera.position.distanceTo(target);

    return (
        <mesh position={target}>
            <sphereGeometry args={[0.018, 8, 6]} />
            <meshBasicMaterial color="#FFFFFF" transparent opacity={distance < 80 ? 0.36 : 0} />
        </mesh>
    );
}
