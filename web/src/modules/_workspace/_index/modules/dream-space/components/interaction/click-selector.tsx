"use client";

import { useEffect } from "react";

import { useThree } from "@react-three/fiber";

import { setDreamHoveredMemory } from "../../stores/dream-store";

export function ClickSelector() {
    const gl = useThree((state) => state.gl);

    useEffect(() => {
        const canvas = gl.domElement;
        const clearHover = () => {
            setDreamHoveredMemory(null);
            canvas.style.cursor = "";
        };

        canvas.addEventListener("pointerdown", clearHover);

        return () => canvas.removeEventListener("pointerdown", clearHover);
    }, [gl]);

    return null;
}
