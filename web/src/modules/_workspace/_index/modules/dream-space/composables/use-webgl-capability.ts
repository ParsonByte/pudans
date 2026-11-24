"use client";

import { useEffect, useState } from "react";

export type WebGLCapability = {
    checked: boolean;
    lowPower: boolean;
    supported: boolean;
};

function detectLowPower(): boolean {
    const cores = navigator.hardwareConcurrency ?? 8;
    const memory =
        "deviceMemory" in navigator ? Number((navigator as Navigator & { deviceMemory?: number }).deviceMemory) : 8;

    return cores <= 4 || memory <= 4;
}

function detectWebGL(): boolean {
    try {
        const canvas = document.createElement("canvas");
        const context = canvas.getContext("webgl2") ?? canvas.getContext("webgl");

        return Boolean(context);
    } catch {
        return false;
    }
}

export function useWebGLCapability(): WebGLCapability {
    const [capability, setCapability] = useState<WebGLCapability>({
        checked: false,
        lowPower: false,
        supported: true,
    });

    useEffect(() => {
        const timer = window.setTimeout(
            () =>
                setCapability({
                    checked: true,
                    lowPower: detectLowPower(),
                    supported: detectWebGL(),
                }),
            0,
        );

        return () => window.clearTimeout(timer);
    }, []);

    return capability;
}
