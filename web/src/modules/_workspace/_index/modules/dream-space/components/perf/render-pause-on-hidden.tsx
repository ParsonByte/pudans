"use client";

import { useEffect } from "react";

import { useThree } from "@react-three/fiber";

import { usePageVisible } from "../../composables/use-page-visible";

export function RenderPauseOnHidden() {
    const visible = usePageVisible();
    const setFrameloop = useThree((state) => state.setFrameloop);

    useEffect(() => {
        setFrameloop(visible ? "always" : "never");

        return () => setFrameloop("always");
    }, [setFrameloop, visible]);

    return null;
}
